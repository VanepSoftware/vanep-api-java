package br.com.vanep.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
          "/oauth2/token",
          "/api/driver-link-codes/consume");

  private final RateLimiter rateLimiter;

  public RateLimitingFilter(RateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !HttpMethod.POST.matches(request.getMethod())
        || !LIMITED_PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String key = clientIp(request) + "|" + request.getRequestURI();
    if (!rateLimiter.tryAcquire(key)) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType("text/plain;charset=UTF-8");
      response.getWriter().write("Muitas tentativas. Tente novamente em instantes.");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwarded)) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
