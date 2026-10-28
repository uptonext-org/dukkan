package de.dukkan.shared.security;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes 401/403 responses as problem+json instead of the default empty bodies. */
@Component
public final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final JsonMapper jsonMapper;

  public ProblemAuthenticationEntryPoint(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
      throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    write(request, response, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, "Authentication is required.");
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
      throws IOException {
    SecurityEvents.accessDenied(
        CurrentUser.optionalId(SecurityContextHolder.getContext()).map(UUID::toString).orElse(null),
        request.getRemoteAddr(),
        request.getMethod(),
        request.getRequestURI());
    write(request, response, HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "Access is denied.");
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String detail)
      throws IOException {
    ProblemDetail problem = Problems.of(status, code, detail, request.getRequestURI());
    response.setStatus(status.value());
    response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }
}
