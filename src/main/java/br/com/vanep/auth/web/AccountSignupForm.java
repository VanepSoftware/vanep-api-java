package br.com.vanep.auth.web;

import br.com.vanep.user.Gender;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class AccountSignupForm {

  @NotBlank(message = "Informe seu nome.")
  private String name;

  @NotBlank(message = "Informe seu e-mail.")
  @Email(message = "E-mail inválido.")
  private String email;

  @NotBlank(message = "Informe uma senha.")
  @Size(min = 6, message = "A senha deve ter ao menos 6 caracteres.")
  private String password;

  @NotBlank(message = "Informe seu documento (CPF).")
  private String document;

  private String phone;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate birthDate;

  private Gender gender;

  @AssertTrue(message = "É necessário aceitar os termos de uso.")
  private boolean acceptTerms;
}
