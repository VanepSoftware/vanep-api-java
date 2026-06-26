package br.com.vanep.auth.web;

import br.com.vanep.user.Gender;
import br.com.vanep.user.UserType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** Dados do passo 2 do cadastro (após o login social) para completar a conta. */
@Getter
@Setter
public class SignupForm {

  @NotNull(message = "Escolha cliente ou motorista.")
  private UserType type;

  private String name;

  @NotBlank(message = "Informe seu documento (CPF).")
  private String document;

  private String phone;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate birthDate;

  private Gender gender;

  @AssertTrue(message = "É necessário aceitar os termos de uso.")
  private boolean acceptTerms;
}
