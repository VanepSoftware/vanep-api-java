package br.com.vanep.user.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.user.dto.OnboardingResponseDTO;
import br.com.vanep.user.enums.OnboardingStep;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
  private final DriverRepository drivers;
  private final DriverServiceAreaRepository areas;

  public OnboardingService(DriverRepository drivers, DriverServiceAreaRepository areas) {
    this.drivers = drivers;
    this.areas = areas;
  }

  @Transactional(readOnly = true)
  public OnboardingResponseDTO findPendingSteps(UserModel user) {
    List<OnboardingStep> pending = new ArrayList<>();

    if (user.getAddressId() == null) {
      pending.add(OnboardingStep.PERSONAL_ADDRESS);
    }

    if (user.getType() == UserType.DRIVER && !hasServiceArea(user)) {
      pending.add(OnboardingStep.SERVICE_AREA);
    }

    return new OnboardingResponseDTO(pending);
  }

  private boolean hasServiceArea(UserModel user) {
    return drivers
        .findByUserId(user.getId())
        .map(driver -> !areas.findByDriverId(driver.getId()).isEmpty())
        .orElse(false);
  }
}
