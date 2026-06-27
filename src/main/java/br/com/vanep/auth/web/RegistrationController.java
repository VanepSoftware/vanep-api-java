package br.com.vanep.auth.web;

import br.com.vanep.user.UserRepository;
import jakarta.validation.Valid;
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

  public RegistrationController(RegistrationService registrationService, UserRepository users) {
    this.registrationService = registrationService;
    this.users = users;
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
    registrationService.registerDriver(form);
    return "redirect:/login?registered";
  }

  private void rejectDuplicates(AccountSignupForm form, BindingResult bindingResult) {
    if (form.getEmail() != null && users.existsByEmail(form.getEmail())) {
      bindingResult.rejectValue("email", "duplicate", "Já existe uma conta com este e-mail.");
    }
    if (form.getDocument() != null && users.existsByDocument(form.getDocument())) {
      bindingResult.rejectValue("document", "duplicate", "Já existe uma conta com este documento.");
    }
  }
}
