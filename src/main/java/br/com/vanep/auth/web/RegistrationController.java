package br.com.vanep.auth.web;

import br.com.vanep.auth.validation.CpfValidator;
import br.com.vanep.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class RegistrationController {

  private final RegistrationService registrationService;
  private final UserRepository users;
  private final MessageSource messages;

  public RegistrationController(
      RegistrationService registrationService, UserRepository users, MessageSource messages) {
    this.registrationService = registrationService;
    this.users = users;
    this.messages = messages;
  }

  @GetMapping("/signup")
  public String choose() {
    return "signup-choose";
  }

  @GetMapping("/signup/client")
  public String clientForm(Model model) {
    if (!model.containsAttribute("clientSignupForm")) {
      model.addAttribute("clientSignupForm", new ClientSignupForm());
    }
    return "signup-client";
  }

  @PostMapping("/signup/client")
  public String registerClient(
      @Valid @ModelAttribute("clientSignupForm") ClientSignupForm form,
      BindingResult bindingResult) {
    rejectDuplicates(form, bindingResult);
    if (bindingResult.hasErrors()) {
      return "signup-client";
    }
    normalizeDocument(form);
    registrationService.registerClient(form);
    return "redirect:/login?registered";
  }

  @GetMapping("/signup/driver")
  public String driverForm(Model model) {
    if (!model.containsAttribute("driverSignupForm")) {
      model.addAttribute("driverSignupForm", new DriverSignupForm());
    }
    return "signup-driver";
  }

  @PostMapping("/signup/driver")
  public String registerDriver(
      @Valid @ModelAttribute("driverSignupForm") DriverSignupForm form,
      BindingResult bindingResult) {
    rejectDuplicates(form, bindingResult);
    if (bindingResult.hasErrors()) {
      return "signup-driver";
    }
    normalizeDocument(form);
    registrationService.registerDriver(form);
    return "redirect:/login?registered";
  }

  @GetMapping("/signup/assistant")
  public String assistantForm(Model model) {
    if (!model.containsAttribute("assistantSignupForm")) {
      model.addAttribute("assistantSignupForm", new AssistantSignupForm());
    }
    return "signup-assistant";
  }

  @PostMapping("/signup/assistant")
  public String registerAssistant(
      @Valid @ModelAttribute("assistantSignupForm") AssistantSignupForm form,
      BindingResult bindingResult) {
    rejectDuplicates(form, bindingResult);
    if (bindingResult.hasErrors()) {
      return "signup-assistant";
    }
    normalizeDocument(form);
    registrationService.registerAssistant(form);
    return "redirect:/login?registered";
  }

  private void rejectDuplicates(AccountSignupForm form, BindingResult bindingResult) {
    if (form.getEmail() != null && users.existsByEmail(form.getEmail())) {
      bindingResult.rejectValue(
          "email",
          "duplicate",
          messages.getMessage(
              "auth.signup.email.duplicate", null, LocaleContextHolder.getLocale()));
    }
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

  private void normalizeDocument(AccountSignupForm form) {
    form.setDocument(CpfValidator.normalize(form.getDocument()));
  }
}
