package br.com.vanep.auth.api;

import br.com.vanep.auth.dto.AssistantSignupRequestDTO;
import br.com.vanep.auth.dto.ClientSignupRequestDTO;
import br.com.vanep.auth.dto.DriverSignupRequestDTO;
import br.com.vanep.auth.dto.SignupResponseDTO;
import br.com.vanep.auth.web.RegistrationService;
import br.com.vanep.user.model.UserModel;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/signup")
public class SignupApiController {

  private final RegistrationService registrationService;

  public SignupApiController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping("/client")
  @ResponseStatus(HttpStatus.CREATED)
  public SignupResponseDTO registerClient(@Valid @RequestBody ClientSignupRequestDTO request) {
    return toResponse(registrationService.registerClient(request));
  }

  @PostMapping("/driver")
  @ResponseStatus(HttpStatus.CREATED)
  public SignupResponseDTO registerDriver(@Valid @RequestBody DriverSignupRequestDTO request) {
    return toResponse(registrationService.registerDriver(request));
  }

  @PostMapping("/assistant")
  @ResponseStatus(HttpStatus.CREATED)
  public SignupResponseDTO registerAssistant(
      @Valid @RequestBody AssistantSignupRequestDTO request) {
    return toResponse(registrationService.registerAssistant(request));
  }

  private static SignupResponseDTO toResponse(UserModel user) {
    return new SignupResponseDTO(user.getEmail(), user.isVerified());
  }
}
