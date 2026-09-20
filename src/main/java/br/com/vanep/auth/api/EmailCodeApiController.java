package br.com.vanep.auth.api;

import br.com.vanep.auth.dto.EmailRequestDTO;
import br.com.vanep.auth.dto.EmailVerifyRequestDTO;
import br.com.vanep.auth.dto.PasswordResetRequestDTO;
import br.com.vanep.auth.exception.InvalidAuthCodeException;
import br.com.vanep.auth.password.PasswordResetService;
import br.com.vanep.auth.verification.EmailVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class EmailCodeApiController {

  private final EmailVerificationService emailVerification;
  private final PasswordResetService passwordReset;

  public EmailCodeApiController(
      EmailVerificationService emailVerification, PasswordResetService passwordReset) {
    this.emailVerification = emailVerification;
    this.passwordReset = passwordReset;
  }

  @PostMapping("/email/verify")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void verifyEmail(@Valid @RequestBody EmailVerifyRequestDTO request) {
    if (!emailVerification.verifyByCode(request.email(), request.code())) {
      throw new InvalidAuthCodeException();
    }
  }

  /** Always accepted: whether a code went out is not something the caller may learn. */
  @PostMapping("/email/verify/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void resendVerification(@Valid @RequestBody EmailRequestDTO request) {
    emailVerification.resend(request.email());
  }

  @PostMapping("/password/forgot")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void forgotPassword(@Valid @RequestBody EmailRequestDTO request) {
    passwordReset.requestReset(request.email());
  }

  @PostMapping("/password/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody PasswordResetRequestDTO request) {
    if (!passwordReset.resetByCode(request.email(), request.code(), request.newPassword())) {
      throw new InvalidAuthCodeException();
    }
  }
}
