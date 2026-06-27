package br.com.vanep.auth.password;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {

  private final PasswordResetService passwordReset;

  public PasswordResetController(PasswordResetService passwordReset) {
    this.passwordReset = passwordReset;
  }

  @GetMapping("/forgot-password")
  public String forgotForm(Model model) {
    if (!model.containsAttribute("forgotPasswordForm")) {
      model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
    }
    return "forgot-password";
  }

  @PostMapping("/forgot-password")
  public String requestReset(
      @Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
      BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
      return "forgot-password";
    }
    passwordReset.requestReset(form.getEmail());

    return "redirect:/login?reset-requested";
  }

  @GetMapping("/reset-password")
  public String resetForm(@RequestParam(required = false) String token, Model model) {
    if (!passwordReset.isValidToken(token)) {
      model.addAttribute("invalid", true);
      return "reset-password";
    }
    if (!model.containsAttribute("resetPasswordForm")) {
      ResetPasswordForm form = new ResetPasswordForm();
      form.setToken(token);
      model.addAttribute("resetPasswordForm", form);
    }
    return "reset-password";
  }

  @PostMapping("/reset-password")
  public String reset(
      @Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
      BindingResult bindingResult) {
    if (!form.passwordsMatch()) {
      bindingResult.rejectValue("confirmPassword", "mismatch", "As senhas não coincidem.");
    }
    if (bindingResult.hasErrors()) {
      return "reset-password";
    }
    if (!passwordReset.reset(form.getToken(), form.getPassword())) {
      bindingResult.reject("invalidToken", "Link inválido ou expirado. Solicite um novo.");
      return "reset-password";
    }
    return "redirect:/login?reset";
  }
}
