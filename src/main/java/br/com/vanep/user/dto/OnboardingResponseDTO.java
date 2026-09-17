package br.com.vanep.user.dto;

import br.com.vanep.user.enums.OnboardingStep;
import java.util.List;

public record OnboardingResponseDTO(List<OnboardingStep> pendingSteps) {
  public OnboardingResponseDTO {
    pendingSteps = pendingSteps == null ? List.of() : List.copyOf(pendingSteps);
  }
}
