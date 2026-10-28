package de.dukkan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Admins fill the shared catalog from a CSV file, e.g. the start catalog before launch. */
class CatalogImportIntegrationTest extends IntegrationTest {

  @Autowired private IdentityService identity;

  private String adminToken() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    identity.grantRole(identity.findByEmail(email).orElseThrow().id(), "ADMIN");
    return login(email);
  }

  private ResultActions upload(String token, byte[] csv, boolean dryRun) throws Exception {
    return mvc.perform(
        multipart("/api/v1/admin/catalog/import")
            .file(new MockMultipartFile("file", "katalog.csv", "text/csv", csv))
            .param("dryRun", String.valueOf(dryRun))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  @Test
  void importsSharedProductsOnceThenMerchantsFindThem() throws Exception {
    String admin = adminToken();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String csv =
        "name_de;name_en;name_ar;unit_size;category;cuisines;keywords;allergens;price\n"
            + "Testgrieß " + suffix + ";Test semolina;سميد;1 kg;grains;TURKISH|ARABIC;irmik;gluten;9,99\n"
            + "Testdatteln " + suffix + ";Test dates;تمر;500 g;sweets;ARABIC;hurma;;\n";

    // Preview first (the default): nothing is written; a price column is ignored.
    upload(admin, csv.getBytes(UTF_8), true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(false))
        .andExpect(jsonPath("$.created").value(2))
        .andExpect(jsonPath("$.rows[0].action").value("CREATE"));
    assertThat(jdbc.sql("SELECT count(*) FROM catalog_product_translation WHERE name LIKE :n").param("n", "%" + suffix).query(Long.class).single()).isZero();

    upload(admin, csv.getBytes(UTF_8), false)
        .andExpect(jsonPath("$.applied").value(true))
        .andExpect(jsonPath("$.created").value(2))
        .andExpect(jsonPath("$.rows[0].productId").isNotEmpty());
    // Shared (no owner, no review), with all three names.
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM catalog_product cp JOIN catalog_product_translation t ON t.catalog_product_id = cp.id"
                        + " WHERE t.locale = 'de' AND t.name = :n AND cp.owner_market_id IS NULL AND cp.review_status IS NULL")
                .param("n", "Testgrieß " + suffix)
                .query(Long.class)
                .single())
        .isEqualTo(1);

    // Importing the same file again skips both.
    upload(admin, csv.getBytes(UTF_8), false)
        .andExpect(jsonPath("$.applied").value(false))
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.existing").value(2))
        .andExpect(jsonPath("$.rows[1].action").value("EXISTS"));

    // Merchants see them as suggestions when adding products.
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Katalog Markt " + suffix);
    MvcResult found =
        mvc.perform(auth(get("/api/v1/merchant/catalog").param("q", "testgrieß " + suffix).param("marketId", market.toString()), merchant)).andReturn();
    assertThat(strings(found, "$.items[*].name")).containsExactly("Testgrieß " + suffix);
  }

  @Test
  void reportsEveryProblemAndWritesNothing() throws Exception {
    String admin = adminToken();
    String csv =
        """
        name_de;name_en;unit_size;category;cuisines
        ;Only English;1 kg;grains;
        Ohne Größe;;;grains;
        Falsche Kategorie;;1 kg;nonsense;
        Falsche Küche;;1 kg;grains;MARTIAN
        Doppelt;;1 kg;grains;
        Doppelt;;1 kg;grains;
        """;
    upload(admin, csv.getBytes(UTF_8), false)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(false))
        .andExpect(jsonPath("$.failed").value(5))
        .andExpect(jsonPath("$.rows[0].errors[0].code").value("PrimaryLocaleRequired"))
        .andExpect(jsonPath("$.rows[1].errors[0].field").value("unit_size"))
        .andExpect(jsonPath("$.rows[2].errors[0].code").value("UnknownCode"))
        .andExpect(jsonPath("$.rows[3].errors[0].field").value("cuisines"))
        .andExpect(jsonPath("$.rows[5].errors[0].code").value("Duplicate"));
    assertThat(jdbc.sql("SELECT count(*) FROM catalog_product_translation WHERE name = 'Doppelt'").query(Long.class).single()).isZero();

    upload(admin, "preis;menge\n1;2\n".getBytes(UTF_8), true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("Header"));
  }

  @Test
  void onlyAdminsImport() throws Exception {
    upload(registerMerchant(), "name_de;unit_size;category\nX;1 kg;grains\n".getBytes(UTF_8), true).andExpect(status().isForbidden());
  }

  /** The start catalog shipped for launch (docs/launch/start-katalog.csv) imports without errors. */
  @Test
  void theStartCatalogIsValid() throws Exception {
    byte[] csv = Files.readAllBytes(Path.of("../../docs/launch/start-katalog.csv"));
    MvcResult preview = upload(adminToken(), csv, true).andExpect(status().isOk()).andReturn();
    assertThat((Integer) read(preview, "$.failed")).as("rows with errors: %s", preview.getResponse().getContentAsString()).isZero();
    assertThat((Integer) read(preview, "$.created") + (Integer) read(preview, "$.existing")).isGreaterThanOrEqualTo(130);
  }
}
