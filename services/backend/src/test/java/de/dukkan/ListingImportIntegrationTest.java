package de.dukkan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.nio.charset.Charset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** B-31: listings from a CSV file, checked first (dry run), then written all at once. */
class ListingImportIntegrationTest extends IntegrationTest {

  /** Excel style: BOM, semicolons, German headers and decimal commas, a quoted field with a line break. */
  private static final String CSV =
      """
      \uFEFFEAN;name_de;name_ar;Beschreibung_de;Marke;Größe;Kategorie;Preis;Verfügbar;Allergene;Küche\r
      2010000000000;;;;;;;2,49 €;ja;;\r
      ;Omas Quittengelee;هلام السفرجل;"nach Familienrezept;
      ohne Zusätze";Oma;250 g;deli;4,50;;;turkish\r
      ;Ayran;;;Sütaş;0,25 l;dairy;0.99;nein;milk;\r
      """;

  ResultActions upload(String token, UUID market, String csv, boolean dryRun) throws Exception {
    return upload(token, market, csv.getBytes(UTF_8), dryRun);
  }

  ResultActions upload(String token, UUID market, byte[] bytes, boolean dryRun) throws Exception {
    return mvc.perform(
        multipart("/api/v1/merchant/markets/" + market + "/listings/import")
            .file(new MockMultipartFile("file", "produkte.csv", "text/csv", bytes))
            .param("dryRun", String.valueOf(dryRun))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  @Test
  void aDryRunReportsTheActionsThenTheImportWritesThemAndIsRepeatable() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Importmarkt");

    MvcResult preview =
        upload(merchant, market, CSV, true)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.applied").value(false))
            .andExpect(jsonPath("$.listed").value(1))
            .andExpect(jsonPath("$.created").value(2))
            .andExpect(jsonPath("$.failed").value(0))
            .andReturn();
    assertThat(strings(preview, "$.rows[*].action")).containsExactly("LIST", "CREATE", "CREATE");
    assertThat(ints(preview, "$.rows[*].line")).containsExactly(2, 3, 4);
    ProductDraftIntegrationTest.conforms("ListingImportResult", object(preview, "$"));
    ProductDraftIntegrationTest.conforms("ListingImportRow", object(preview, "$.rows[0]"));
    // Nothing written yet.
    assertThat(listingCount(market)).isZero();

    MvcResult imported = upload(merchant, market, CSV, false).andExpect(status().isOk()).andExpect(jsonPath("$.applied").value(true)).andReturn();
    assertThat(listingCount(market)).isEqualTo(3);
    String jelly = read(imported, "$.rows[1].listingId");
    mvc.perform(get("/api/v1/products/" + jelly).header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
        .andExpect(jsonPath("$.name").value("Omas Quittengelee"))
        .andExpect(jsonPath("$.description").value("nach Familienrezept;\nohne Zusätze"))
        .andExpect(jsonPath("$.brand").value("Oma"))
        .andExpect(jsonPath("$.price.amountCents").value(450));
    mvc.perform(get("/api/v1/products/" + read(imported, "$.rows[2].listingId")))
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.allergens[0]").value("milk"));

    // The same file again (new prices): matched by barcode and by name + size, so only updates.
    MvcResult again =
        upload(merchant, market, CSV.replace("2,49 €", "2,29").replace("4,50", "4,90"), false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(3))
            .andReturn();
    assertThat(read(again, "$.rows[1].listingId").toString()).isEqualTo(jelly);
    assertThat(listingCount(market)).isEqualTo(3);
    mvc.perform(get("/api/v1/products/" + jelly)).andExpect(jsonPath("$.price.amountCents").value(490));
  }

  @Test
  void oneBadRowStopsTheWholeImportAndEveryProblemIsReported() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Fehlermarkt");
    String csv =
        """
        name_de,unit_size,category,price,max_order_quantity,barcode
        Simit,1 Stück,bakery,"0,80",,
        Lahmacun,,bakery,abc,120,
        Börek,1 Stück,space-food,2.50,,
        Simit,1 stück,bakery,0.90,,
        ,,,1.00,,12
        """;
    MvcResult result =
        upload(merchant, market, csv, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").value(false))
            .andExpect(jsonPath("$.failed").value(4))
            .andReturn();
    assertThat(strings(result, "$.rows[*].action")).containsExactly("CREATE", "ERROR", "ERROR", "ERROR", "ERROR");
    assertThat(strings(result, "$.rows[1].errors[*].field")).contains("price", "max_order_quantity", "unit_size");
    assertThat(strings(result, "$.rows[2].errors[*].code")).containsExactly("UnknownCode");
    assertThat(strings(result, "$.rows[3].errors[*].code")).containsExactly("Duplicate");
    assertThat(strings(result, "$.rows[4].errors[*].field")).contains("barcode");
    assertThat(listingCount(market)).isZero();
  }

  @Test
  void filesMustBeReadableAndBelongToTheMerchant() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Dateimarkt");
    upload(merchant, market, "name_de;menge\nSimit;1\n", true)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("file"))
        .andExpect(jsonPath("$.errors[0].code").value("Header"));
    upload(merchant, market, "name_de;preis\nÇay;1\n".getBytes(Charset.forName("windows-1254")), true)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("Encoding"));
    upload(merchant, market, "name_de;preis\n" + "Simit;1\n".repeat(1001), true)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("TooManyRows"));
    // Another merchant's market does not exist for this merchant.
    upload(registerMerchant(), market, CSV, true).andExpect(status().isNotFound());
    upload(registerUser(), market, CSV, true).andExpect(status().isForbidden());
  }

  private long listingCount(UUID market) {
    return jdbc.sql("SELECT count(*) FROM market_product WHERE market_id = :m AND deleted_at IS NULL").param("m", market).query(Long.class).single();
  }

}
