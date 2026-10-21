package de.dukkan.shared.error;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every exception into an RFC 9457 problem response carrying a {@code code} and, for
 * validation failures, an {@code errors[]} list. Stack traces and internal messages never leave
 * the server.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** One entry of the {@code errors[]} array; {@code code} is the constraint name, e.g. NotBlank. */
  public record FieldViolation(String field, String code, String message) {}

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
    HttpStatus status =
        switch (ex) {
          case NotFoundException _ -> HttpStatus.NOT_FOUND;
          case ConflictException _ -> HttpStatus.CONFLICT;
          case BusinessRuleException _ -> HttpStatus.UNPROCESSABLE_CONTENT;
          case InvalidCredentialsException _ -> HttpStatus.UNAUTHORIZED;
          case InvalidRequestException _ -> HttpStatus.BAD_REQUEST;
          case UnauthorizedException _ -> HttpStatus.UNAUTHORIZED;
          case RateLimitedException _ -> HttpStatus.TOO_MANY_REQUESTS;
          case ContentRejectedException rejected ->
              switch (rejected.reason()) {
                case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
                case UNSUPPORTED_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                case INVALID -> HttpStatus.BAD_REQUEST;
              };
        };
    if (ex instanceof InvalidRequestException invalid) {
      ProblemDetail problem =
          Problems.of(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "The request failed validation.", request.getRequestURI());
      problem.setTitle("Validation failed");
      problem.setProperty(
          "errors", List.of(new FieldViolation(invalid.field(), invalid.violation(), invalid.getMessage())));
      return ResponseEntity.status(status).contentType(APPLICATION_PROBLEM_JSON).body(problem);
    }
    if (ex instanceof RateLimitedException limited) {
      return ResponseEntity.status(status)
          .header(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfterSeconds()))
          .contentType(APPLICATION_PROBLEM_JSON)
          .body(Problems.of(status, ex.code(), ex.getMessage(), request.getRequestURI()));
    }
    return problem(status, ex.code(), ex.getMessage(), request);
  }

  /** A malformed multipart body (the size limit has its own handler and answers 413). */
  @ExceptionHandler(MultipartException.class)
  ResponseEntity<ProblemDetail> handleMultipart(MultipartException ex, HttpServletRequest request) {
    LOG.info("Malformed multipart request: {}", ex.getClass().getSimpleName());
    ProblemDetail problem =
        Problems.of(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "The request failed validation.", request.getRequestURI());
    problem.setTitle("Validation failed");
    problem.setProperty("errors", List.of(new FieldViolation("body", "NotReadable", "is not a valid multipart/form-data body")));
    return ResponseEntity.badRequest().contentType(APPLICATION_PROBLEM_JSON).body(problem);
  }

  @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
  ResponseEntity<ProblemDetail> handleConcurrentModification(RuntimeException ex, HttpServletRequest request) {
    LOG.info("Concurrent modification: {}", ex.getClass().getSimpleName());
    return problem(
        HttpStatus.CONFLICT,
        ErrorCodes.CONCURRENT_MODIFICATION,
        "The resource was modified concurrently. Please retry.",
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
    LOG.info("Data integrity violation", ex);
    return problem(HttpStatus.CONFLICT, ErrorCodes.CONFLICT, "The request conflicts with existing data.", request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
    LOG.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred.", request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    List<FieldViolation> violations = new ArrayList<>();
    ex.getBindingResult().getGlobalErrors().forEach(e -> violations.add(violation(e.getObjectName(), e)));
    ex.getBindingResult().getFieldErrors().forEach(e -> violations.add(violation(e.getField(), e)));
    return validationProblem(violations, headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    List<FieldViolation> violations = new ArrayList<>();
    ex.getParameterValidationResults()
        .forEach(
            result -> {
              String parameter = result.getMethodParameter().getParameterName();
              result
                  .getResolvableErrors()
                  .forEach(
                      error ->
                          violations.add(
                              violation(
                                  error instanceof FieldError fieldError
                                      ? parameter + "." + fieldError.getField()
                                      : parameter,
                                  error)));
            });
    return validationProblem(violations, headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return validationProblem(
        List.of(new FieldViolation(ex.getParameterName(), "NotNull", "is required")), headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestPart(
      MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return validationProblem(
        List.of(new FieldViolation(ex.getRequestPartName(), "NotNull", "is required")), headers, request);
  }

  /** Spring answers 413 when a multipart file exceeds {@code spring.servlet.multipart.max-file-size}. */
  @Override
  protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The uploaded file is too large.");
    problem.setProperty("code", ErrorCodes.FILE_TOO_LARGE);
    return handleExceptionInternal(ex, problem, headers, HttpStatus.CONTENT_TOO_LARGE, request);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String field = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
    return validationProblem(
        List.of(new FieldViolation(field == null ? "request" : field, "TypeMismatch", "has an invalid format")),
        headers,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return validationProblem(
        List.of(new FieldViolation("body", "NotReadable", "is missing or not valid JSON")), headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleServletRequestBindingException(
      ServletRequestBindingException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (ex instanceof MissingRequestHeaderException missing) {
      return validationProblem(
          List.of(new FieldViolation(missing.getHeaderName(), "NotNull", "is required")), headers, request);
    }
    return super.handleServletRequestBindingException(ex, headers, status, request);
  }

  /** Ensures every framework-generated problem carries a contract {@code code}. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    ProblemDetail problem =
        body instanceof ProblemDetail existing
            ? existing
            : ProblemDetail.forStatusAndDetail(statusCode, "The request could not be processed.");
    if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
      problem.setProperty("code", codeFor(statusCode));
    }
    if (problem.getInstance() == null && request instanceof ServletWebRequest servlet) {
      problem.setInstance(URI.create(servlet.getRequest().getRequestURI()));
    }
    Problems.withType(problem);
    return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
  }

  private ResponseEntity<Object> validationProblem(
      List<FieldViolation> violations, HttpHeaders headers, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request failed validation.");
    problem.setTitle("Validation failed");
    problem.setProperty("code", ErrorCodes.VALIDATION_FAILED);
    problem.setProperty("errors", violations);
    return handleExceptionInternal(new IllegalArgumentException(), problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  private static FieldViolation violation(String field, MessageSourceResolvable error) {
    String message = error.getDefaultMessage();
    String[] codes = error.getCodes();
    // Bean Validation codes run from most to least specific; the last one is the constraint name.
    String code = codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
    return new FieldViolation(field, code, message == null ? "is invalid" : message);
  }

  private static String codeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> ErrorCodes.VALIDATION_FAILED;
      case 401 -> ErrorCodes.UNAUTHORIZED;
      case 403 -> ErrorCodes.FORBIDDEN;
      case 404 -> ErrorCodes.NOT_FOUND;
      case 405 -> ErrorCodes.METHOD_NOT_ALLOWED;
      case 409 -> ErrorCodes.CONFLICT;
      case 413 -> ErrorCodes.FILE_TOO_LARGE;
      case 415 -> ErrorCodes.UNSUPPORTED_MEDIA_TYPE;
      default -> status.is5xxServerError() ? ErrorCodes.INTERNAL_ERROR : "HTTP_" + status.value();
    };
  }

  static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    ProblemDetail problem = Problems.of(status, code, detail, request.getRequestURI());
    return ResponseEntity.status(status).contentType(APPLICATION_PROBLEM_JSON).body(problem);
  }
}
