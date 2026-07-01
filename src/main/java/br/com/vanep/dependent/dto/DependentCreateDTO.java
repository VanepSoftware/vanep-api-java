package br.com.vanep.dependent.dto;

import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.user.Gender;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DependentCreateDTO {

  @NotBlank(message = "Nome é obrigatório.")
  private String name;

  private LocalDate birthDate;

  private Gender gender;

  private String document;

  private String phone;

  private String email;

  private Boolean isSelf;

  private Boolean isDefault;

  private Shift shift;

  private String schoolToken;

  private String addressToken;

  private String clientToken;
}
