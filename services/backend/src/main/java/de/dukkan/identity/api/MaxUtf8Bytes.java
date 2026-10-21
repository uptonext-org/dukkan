package de.dukkan.identity.api;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jspecify.annotations.Nullable;

/**
 * The annotated string encodes to at most {@link #value()} UTF-8 bytes. Passwords need this because
 * bcrypt only accepts 72 bytes, which an Arabic password reaches at about 36 characters.
 */
@Documented
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

  int value();

  String message() default "must not be longer than {value} bytes (UTF-8)";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  final class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {
    private int max;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
      max = annotation.value();
    }

    @Override
    public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
      return value == null || value.getBytes(UTF_8).length <= max;
    }
  }
}
