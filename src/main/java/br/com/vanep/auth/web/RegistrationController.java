package br.com.vanep.auth.web;

import br.com.vanep.auth.exception.SignupDuplicateException;
import br.com.vanep.auth.mapper.SignupFormMapper;
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
  private final SignupFormMapper signupFormMapper;
  private final MessageSource messages;

  public RegistrationController(
      RegistrationService registrationService,
      SignupFormMapper signupFormMapper,
      MessageSource messages) {
    this.registrationService = registrationService;
    this.signupFormMapper = signupFormMapper;
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
    if (bindingResult.hasErrors()) {
      return "signup-client";
    }
    return saveRegistration(
        () -> registrationService.registerClient(signupFormMapper.toRequest(form)),
        bindingResult,
        "signup-client");
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
    if (bindingResult.hasErrors()) {
      return "signup-driver";
    }
    return saveRegistration(
        () -> registrationService.registerDriver(signupFormMapper.toRequest(form)),
        bindingResult,
        "signup-driver");
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
    if (bindingResult.hasErrors()) {
      return "signup-assistant";
    }
    return saveRegistration(
        () -> registrationService.registerAssistant(signupFormMapper.toRequest(form)),
        bindingResult,
        "signup-assistant");
  }

  String saveRegistration(Runnable registration, BindingResult bindingResult, String formView) {
    try {
      registration.run();
    } catch (SignupDuplicateException duplicate) {
      bindingResult.rejectValue(
          duplicate.getField(),
          "duplicate",
          messages.getMessage(duplicate.getMessageKey(), null, LocaleContextHolder.getLocale()));
      return formView;
    }
    return "redirect:/login?registered";
  }
}
