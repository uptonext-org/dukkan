package de.dukkan.address.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private static AddressRequest withPostalCode(String postalCode) {
    return new AddressRequest("Home", "Musterweg", "12a", null, postalCode, "Hamburg", null, null, null);
  }

  private static Set<String> invalidFields(AddressRequest request) {
    return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
  }

  @Test
  void validAddressPasses() {
    assertThat(invalidFields(withPostalCode("41460"))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"4146", "414600", "4146a", "", " 41460"})
  void postalCodeMustBeFiveDigits(String postalCode) {
    assertThat(invalidFields(withPostalCode(postalCode))).containsExactly("postalCode");
  }

  @Test
  void requiredFieldsAndLengths() {
    AddressRequest request =
        new AddressRequest("l".repeat(51), "", "12345678901", null, null, " ", "d".repeat(301), null, true);

    assertThat(invalidFields(request))
        .containsExactlyInAnyOrder("label", "street", "houseNumber", "postalCode", "city", "deliveryInstructions");
  }

  @Test
  void locationMustBeValidCoordinates() {
    AddressRequest request =
        new AddressRequest(null, "Kardamomweg", "1", null, "20099", "Hamburg", null, new de.dukkan.shared.api.GeoPoint(91.0, 10.0), null);

    assertThat(invalidFields(request)).containsExactly("location.lat");
  }

  @Test
  void blankOptionalFieldsAreStoredAsNull() {
    AddressRequest request = new AddressRequest(" ", " Musterweg ", "1", "", "20099", "Hamburg", "  ", null, null);

    assertThat(request.toDetails().label()).isNull();
    assertThat(request.toDetails().addition()).isNull();
    assertThat(request.toDetails().deliveryInstructions()).isNull();
    assertThat(request.toDetails().street()).isEqualTo("Musterweg");
    assertThat(request.makeDefault()).isFalse();
  }
}
