package br.com.vanep.auth.security;

import br.com.vanep.shared.error.ApiProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final Set<String> LIMITED_PATHS =
      Set.of(
          "/login",
          "/signup/client",
          "/signup/driver",
          "/signup/assistant",
          "/forgot-password",
          "/reset-password",
          "/verify-email/resend",
          "/oauth2/token");

  private static final String LIMITED_PATH_PREFIX = "/api/auth/";

  private final RateLimiter rateLimiter;
  private final ApiProblemResponseWriter problemWriter;

  @Autowired
  public RateLimitingFilter(RateLimiter rateLimiter, ApiProblemResponseWriter problemWriter) {
    this.rateLimiter = rateLimiter;
    this.problemWriter = problemWriter;
  }

  RateLimitingFilter(RateLimiter rateLimiter) {
    this(rateLimiter, null);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !HttpMethod.POST.matches(request.getMethod()) || !isLimited(request.getRequestURI());
  }

  private static boolean isLimited(String path) {
    return LIMITED_PATHS.contains(path) || path.startsWith(LIMITED_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Never read a forwarding header here: the caller controls it. Only the trusted
    // proxy configured in server.tomcat.remoteip may rewrite getRemoteAddr().
    String key = request.getRemoteAddr() + "|" + request.getRequestURI();
    if (!rateLimiter.tryAcquire(key)) {
      if (problemWriter != null && request.getRequestURI().startsWith("/api/")) {
        problemWriter.write(
            request, response, HttpStatus.TOO_MANY_REQUESTS, "request.rate_limited");
        return;
      }
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType("text/plain;charset=UTF-8");
      response.getWriter().write("Muitas tentativas. Tente novamente em instantes.");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
