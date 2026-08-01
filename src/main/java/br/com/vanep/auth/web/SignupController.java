package br.com.vanep.auth.web;

import br.com.vanep.auth.oauth.OAuthAccountService;
import br.com.vanep.auth.validation.CpfValidator;
import br.com.vanep.user.AuthProvider;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.model.UserModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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

@Controller
public class SignupController {

  private final OAuthAccountService accounts;
  private final UserRepository users;
  private final MessageSource messages;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();
  private final RequestCache requestCache = new HttpSessionRequestCache();

  public SignupController(
      OAuthAccountService accounts, UserRepository users, MessageSource messages) {
    this.accounts = accounts;
    this.users = users;
    this.messages = messages;
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
    rejectDuplicateDocument(form, bindingResult);
    if (bindingResult.hasErrors()) {
      model.addAttribute("email", principal.getEmail());
      model.addAttribute("name", principal.getFullName());
      return "signup-complete";
    }

    form.setDocument(CpfValidator.normalize(form.getDocument()));

    UserModel user =
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

  private void rejectDuplicateDocument(SignupForm form, BindingResult bindingResult) {
    if (bindingResult.hasFieldErrors("document")) {
      return;
    }
    String document = CpfValidator.normalize(form.getDocument());
    if (!document.isEmpty() && users.existsByDocument(document)) {
      bindingResult.rejectValue(
          "document",
          "duplicate",
          messages.getMessage(
              "auth.signup.document.duplicate", null, LocaleContextHolder.getLocale()));
    }
  }

  private void reauthenticate(
      UserModel user, HttpServletRequest request, HttpServletResponse response) {

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
