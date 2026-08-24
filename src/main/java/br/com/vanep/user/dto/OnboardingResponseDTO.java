package br.com.vanep.user.dto;

import br.com.vanep.user.enums.OnboardingStep;
import java.util.List;

/** O que ainda falta no cadastro do usuário. Lista vazia significa cadastro completo. */
public record OnboardingResponseDTO(List<OnboardingStep> pendingSteps) {

  public OnboardingResponseDTO {
    pendingSteps = pendingSteps == null ? List.of() : List.copyOf(pendingSteps);
  }
}
