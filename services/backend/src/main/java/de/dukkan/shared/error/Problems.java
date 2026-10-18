package de.dukkan.shared.error;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds problem details that carry every field the contract requires. */
public final class Problems {

  /** RFC 9457 default type. Spring 7 leaves {@code type} unset, but the contract requires it. */
  public static final URI BLANK_TYPE = URI.create("about:blank");

  private Problems() {}

  public static ProblemDetail of(HttpStatusCode status, String code, String detail, String instance) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    problem.setInstance(URI.create(instance));
    return withType(problem);
  }

  public static ProblemDetail withType(ProblemDetail problem) {
    if (problem.getType() == null) {
      problem.setType(BLANK_TYPE);
    }
    return problem;
  }
}
