package br.com.vanep.auth.web;

import br.com.vanep.auth.validation.Cpf;
import br.com.vanep.auth.validation.StrongPassword;
import br.com.vanep.user.enums.Gender;
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

  @NotBlank(message = "{auth.signup.name.required}")
  private String name;

  @NotBlank(message = "{auth.signup.email.required}")
  @Email(message = "{auth.signup.email.invalid}")
  private String email;

  @NotBlank(message = "{auth.signup.password.required}")
  @Size(min = 6, message = "{auth.signup.password.min}")
  @StrongPassword
  private String password;

  @NotBlank(message = "{auth.signup.document.required}")
  @Cpf
  private String document;

  private String phone;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate birthDate;

  private Gender gender;

  @AssertTrue(message = "{auth.signup.terms.required}")
  private boolean acceptTerms;
}
