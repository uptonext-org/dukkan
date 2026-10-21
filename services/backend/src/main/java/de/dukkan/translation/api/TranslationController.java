package de.dukkan.translation.api;

import de.dukkan.shared.security.CurrentUser;
import de.dukkan.translation.application.TranslationService;
import de.dukkan.translation.application.TranslationService.Field;
import de.dukkan.translation.application.Translator.TextKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Machine translation suggestions for merchant texts (B-38); the merchant reviews them before saving. */
@RestController
final class TranslationController {

  private final TranslationService translations;

  TranslationController(TranslationService translations) {
    this.translations = translations;
  }

  record FieldDto(
      @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String key,
      @NotEmpty @Size(max = 10) Map<@Pattern(regexp = "^[a-z]{2}$") String, @Size(max = 2000) String> values) {}

  record TranslationRequest(@NotNull TextKind kind, @NotEmpty @Size(max = TranslationService.MAX_FIELDS) List<@Valid @NotNull FieldDto> fields) {}

  record TranslationResponse(List<FieldDto> fields) {}

  @PostMapping("/api/v1/merchant/translations")
  TranslationResponse translate(@CurrentSecurityContext SecurityContext security, @RequestBody @Valid TranslationRequest request) {
    List<Field> fields = request.fields().stream().map(f -> new Field(f.key(), f.values())).toList();
    List<Field> translated = translations.complete(CurrentUser.id(security), request.kind(), fields);
    return new TranslationResponse(translated.stream().map(f -> new FieldDto(f.key(), f.values())).toList());
  }
}
