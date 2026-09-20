package br.com.vanep.auth.password;

import br.com.vanep.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordForm {

  @NotBlank private String token;

  @NotBlank(message = "{auth.signup.password.required}")
  @Size(min = 8, message = "{auth.password.reset.min}")
  @StrongPassword
  private String password;

  @NotBlank(message = "{auth.password.reset.confirm.required}")
  private String confirmPassword;

  public boolean passwordsMatch() {
    return password != null && password.equals(confirmPassword);
  }
}
