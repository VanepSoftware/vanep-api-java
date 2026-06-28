package br.com.vanep.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SsoLogoutController {

  private final List<String> allowedRedirectUris;

  public SsoLogoutController(
      @Value("${vanep.oauth.client.post-logout-redirect-uris:}") List<String> allowedRedirectUris) {
    this.allowedRedirectUris = allowedRedirectUris;
  }

  @GetMapping("/auth/sso-logout")
  public String ssoLogout(
      HttpServletRequest request,
      @RequestParam(name = "redirect_uri", required = false) String redirectUri) {

    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();

    if (redirectUri != null && allowedRedirectUris.contains(redirectUri)) {
      return "redirect:" + redirectUri;
    }

    if (!allowedRedirectUris.isEmpty()) {
      String first = allowedRedirectUris.stream().filter(u -> !u.isBlank()).findFirst().orElse(null);
      if (first != null) {
        return "redirect:" + first;
      }
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid redirect URI");
  }
}
