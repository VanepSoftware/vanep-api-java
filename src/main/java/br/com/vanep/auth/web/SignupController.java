package br.com.vanep.auth.web;

import br.com.vanep.auth.oauth.OAuthAccountService;
import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Passo 2 do cadastro via login social: coleta os dados obrigatórios (tipo, documento, etc.) que o
 * provedor não fornece e cria a conta Vanep, religando a sessão como o usuário recém-criado.
 */
@Controller
public class SignupController {

  private final OAuthAccountService accounts;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();
  private final RequestCache requestCache = new HttpSessionRequestCache();

  public SignupController(OAuthAccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/signup/complete")
  public String form(@AuthenticationPrincipal OidcUser principal, Model model) {
    if (principal == null) {
      return "redirect:/login";
    }
    model.addAttribute("email", principal.getEmail());
    model.addAttribute("name", principal.getFullName());
    if (!model.containsAttribute("signupForm")) {
      model.addAttribute("signupForm", new SignupForm());
    }
    return "signup-complete";
  }

  @PostMapping("/signup/complete")
  public String submit(
      @AuthenticationPrincipal OidcUser principal,
      @Valid @ModelAttribute("signupForm") SignupForm form,
      BindingResult bindingResult,
      Model model,
      HttpServletRequest request,
      HttpServletResponse response) {

    if (principal == null) {
      return "redirect:/login";
    }
    if (bindingResult.hasErrors()) {
      model.addAttribute("email", principal.getEmail());
      model.addAttribute("name", principal.getFullName());
      return "signup-complete";
    }

    User user =
        accounts.completeRegistration(
            AuthProvider.GOOGLE,
            principal.getSubject(),
            principal.getEmail(),
            principal.getFullName(),
            form);

    reauthenticate(user, request, response);

    SavedRequest saved = requestCache.getRequest(request, response);
    return "redirect:" + (saved != null ? saved.getRedirectUrl() : "/");
  }

  /**
   * Troca a autenticação social pela conta Vanep recém-criada (nome = e-mail → vira o sub do JWT).
   */
  private void reauthenticate(User user, HttpServletRequest request, HttpServletResponse response) {
    // Troca o id de sessão ao elevar o privilégio (de pré-cadastro social para conta completa)
    // para evitar session fixation.
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getType().name())));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
  }
}
