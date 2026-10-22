package de.dukkan.privacy.api;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.privacy.application.AccountService;
import de.dukkan.privacy.application.AccountService.AccountExport;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Data export and account deletion for the signed-in user (B-51). */
@RequestMapping("/api/v1/me")
@RestController
final class AccountController {

  record DeleteAccountRequest(@Nullable @Size(max = 200) String password) {}

  private final AccountService accounts;

  AccountController(AccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/export")
  ResponseEntity<AccountExport> export(
      @CurrentSecurityContext SecurityContext security, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dukkan-daten.json\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(accounts.export(CurrentUser.id(security), locale));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@CurrentSecurityContext SecurityContext security, @RequestBody(required = false) @Valid @Nullable DeleteAccountRequest request) {
    accounts.delete(CurrentUser.id(security), request == null ? null : request.password());
  }
}
