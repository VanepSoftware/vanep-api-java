package br.com.vanep.auth.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordForm {

  @NotBlank(message = "{auth.signup.email.required}")
  @Email(message = "{auth.signup.email.invalid}")
  private String email;
}
