package br.com.vanep.auth.web;

import br.com.vanep.auth.validation.Cpf;
import br.com.vanep.user.Gender;
import br.com.vanep.user.UserType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class SignupForm {

  @NotNull(message = "{auth.signup.type.required}")
  private UserType type;

  private String name;

  @NotBlank(message = "Informe seu documento (CPF).")
  @Cpf
  private String document;

  private String phone;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate birthDate;

  private Gender gender;

  @AssertTrue(message = "É necessário aceitar os termos de uso.")
  private boolean acceptTerms;
}
