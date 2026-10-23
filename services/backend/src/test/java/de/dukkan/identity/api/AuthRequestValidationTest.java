package de.dukkan.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.identity.api.AuthDtos.LoginRequest;
import de.dukkan.identity.api.AuthDtos.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthRequestValidationTest {

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

  private static Set<String> invalidFields(Object request) {
    Set<ConstraintViolation<Object>> violations = validator.validate(request);
    return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
  }

  @Test
  void validRegistrationPasses() {
    assertThat(invalidFields(new RegisterRequest("anna@example.org", "correct-horse", "Anna", "Muster", null, null, null))).isEmpty();
  }

  @Test
  void registrationRejectsBadEmailShortPasswordAndBlankNames() {
    assertThat(invalidFields(new RegisterRequest("not-an-email", "short", " ", "", null, null, null)))
        .containsExactlyInAnyOrder("email", "password", "firstName", "lastName");
  }

  @Test
  void registrationRejectsTooLongValues() {
    String longName = "x".repeat(101);
    assertThat(invalidFields(new RegisterRequest("anna@example.org", "p".repeat(129), longName, longName, null, null, null)))
        .containsExactlyInAnyOrder("password", "firstName", "lastName");
  }

  @Test
  void loginRequiresEmailAndPassword() {
    assertThat(invalidFields(new LoginRequest(null, null))).containsExactlyInAnyOrder("email", "password");
    assertThat(invalidFields(new LoginRequest("anna@example.org", "x"))).isEmpty();
  }

  @Test
  void passwordsAreNotPrinted() {
    assertThat(new RegisterRequest("a@b.de", "secret-password", "A", "B", null, null, null).toString()).doesNotContain("secret-password");
    assertThat(new LoginRequest("a@b.de", "secret-password").toString()).doesNotContain("secret-password");
  }
}
