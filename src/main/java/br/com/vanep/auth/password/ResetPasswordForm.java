package br.com.vanep.auth.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Nova senha + confirmação, carregando o token do link. */
@Getter
@Setter
public class ResetPasswordForm {

  @NotBlank private String token;

  @NotBlank(message = "Informe a nova senha.")
  @Size(min = 8, message = "A senha deve ter ao menos 8 caracteres.")
  private String password;

  @NotBlank(message = "Confirme a nova senha.")
  private String confirmPassword;

  public boolean passwordsMatch() {
    return password != null && password.equals(confirmPassword);
  }
}
