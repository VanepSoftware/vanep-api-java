package br.com.vanep.auth.verification;

import br.com.vanep.user.exception.ProfileEmailDuplicateException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class EmailVerificationController {

  private final EmailVerificationService verification;

  public EmailVerificationController(EmailVerificationService verification) {
    this.verification = verification;
  }

  @GetMapping("/verify-email")
  public String verify(@RequestParam(required = false) String token, Model model) {
    try {
      if (verification.verify(token)) {
        return "redirect:/login?verified";
      }
      model.addAttribute("invalid", true);
      return "verify-email";
    } catch (ProfileEmailDuplicateException ex) {
      return "redirect:/login?email_taken";
    }
  }

  @PostMapping("/verify-email/resend")
  public String resend(@RequestParam(required = false) String email) {
    if (email != null && !email.isBlank()) {
      verification.resend(email);
    }

    return "redirect:/login?verify-sent";
  }
}
