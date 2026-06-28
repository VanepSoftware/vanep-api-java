package br.com.vanep.auth.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordForm {

  @NotBlank(message = "Informe o e-mail.")
  @Email(message = "E-mail inválido.")
  private String email;
}
