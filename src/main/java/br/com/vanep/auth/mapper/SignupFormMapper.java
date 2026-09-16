package br.com.vanep.auth.mapper;

import br.com.vanep.auth.dto.AccountSignupRequestDTO;
import br.com.vanep.auth.dto.AssistantSignupRequestDTO;
import br.com.vanep.auth.dto.ClientSignupRequestDTO;
import br.com.vanep.auth.dto.DriverSignupRequestDTO;
import br.com.vanep.auth.web.AccountSignupForm;
import br.com.vanep.auth.web.AssistantSignupForm;
import br.com.vanep.auth.web.ClientSignupForm;
import br.com.vanep.auth.web.DriverSignupForm;
import org.springframework.stereotype.Component;

@Component
public class SignupFormMapper {

  public ClientSignupRequestDTO toRequest(ClientSignupForm form) {
    return copyAccountFields(form, new ClientSignupRequestDTO());
  }

  public AssistantSignupRequestDTO toRequest(AssistantSignupForm form) {
    return copyAccountFields(form, new AssistantSignupRequestDTO());
  }

  public DriverSignupRequestDTO toRequest(DriverSignupForm form) {
    DriverSignupRequestDTO request = copyAccountFields(form, new DriverSignupRequestDTO());
    request.setCnpj(form.getCnpj());
    request.setExperienceYears(form.getExperienceYears());
    request.setBasePrice(form.getBasePrice());
    return request;
  }

  private <T extends AccountSignupRequestDTO> T copyAccountFields(
      AccountSignupForm form, T request) {
    request.setName(form.getName());
    request.setEmail(form.getEmail());
    request.setPassword(form.getPassword());
    request.setDocument(form.getDocument());
    request.setPhone(form.getPhone());
    request.setBirthDate(form.getBirthDate());
    request.setGender(form.getGender());
    request.setAcceptTerms(form.isAcceptTerms());
    return request;
  }
}
