package de.dukkan.i18n.api;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.LocaleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code Accept-Language} against the enabled locales once per API request, stores the
 * {@link ContentLocale} as request attribute {@link ContentLocale#REQUEST_ATTRIBUTE} (controllers bind it
 * with {@code @RequestAttribute}) and sets {@code Content-Language}.
 */
@Component
public final class ContentLocaleFilter extends OncePerRequestFilter {

  private final LocaleService locales;

  public ContentLocaleFilter(LocaleService locales) {
    this.locales = locales;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    ContentLocale locale = locales.contentLocale(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
    request.setAttribute(ContentLocale.REQUEST_ATTRIBUTE, locale);
    response.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.effective());
    chain.doFilter(request, response);
  }
}
