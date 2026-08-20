package br.com.vanep.dependent.dto;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.user.enums.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DependentCreateDTO {

  @NotBlank(message = "{dependent.name.blank}")
  @Size(max = 255, message = "{dependent.name.too_long}")
  private String name;

  private LocalDate birthDate;

  private Gender gender;

  @Size(max = 64, message = "{dependent.document.too_long}")
  private String document;

  @Size(max = 32, message = "{dependent.phone.too_long}")
  private String phone;

  @Email(message = "{user.profile.email.invalid}")
  @Size(max = 255, message = "{user.profile.email.too_long}")
  private String email;

  private Boolean isSelf;

  private Boolean isDefault;

  private Shift shift;

  private String schoolToken;

  @Valid private AddressRequestDTO address;

  private String clientToken;
}
