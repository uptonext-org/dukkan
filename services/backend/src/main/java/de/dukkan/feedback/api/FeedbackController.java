package de.dukkan.feedback.api;

import de.dukkan.feedback.application.FeedbackService;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customers write feedback to a market while its merchant takes feedback. */
@RestController
final class FeedbackController {

  record FeedbackRequest(@Nullable @Min(1) @Max(5) Integer rating, @NotBlank @Size(max = FeedbackService.MAX_LENGTH) String message) {}

  private final FeedbackService feedback;

  FeedbackController(FeedbackService feedback) {
    this.feedback = feedback;
  }

  @GetMapping("/api/v1/markets/{marketId}/feedback")
  FeedbackService.Availability availability(@PathVariable UUID marketId) {
    return feedback.availability(marketId);
  }

  @PostMapping("/api/v1/markets/{marketId}/feedback")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void submit(@CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId, @Valid @RequestBody FeedbackRequest request) {
    feedback.submit(CurrentUser.id(security), marketId, request.rating(), request.message());
  }
}
