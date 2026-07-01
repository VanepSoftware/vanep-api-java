package br.com.vanep.dependent.dto;

import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.user.Gender;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DependentUpdateDTO {

  private String name;

  private LocalDate birthDate;

  private Gender gender;

  private String document;

  private String phone;

  private String email;

  private Boolean isSelf;

  private Boolean isDefault;

  private Shift shift;

  private Long schoolId;

  private Long addressId;
}
