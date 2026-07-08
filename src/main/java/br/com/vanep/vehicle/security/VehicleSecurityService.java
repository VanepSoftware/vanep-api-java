package br.com.vanep.vehicle.security;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.vehicle.repository.VehicleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("vehicleSecurity")
public class VehicleSecurityService {

  private final VehicleRepository repository;

  public VehicleSecurityService(VehicleRepository repository) {
    this.repository = repository;
  }

  public boolean isOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            callerUid ->
                repository
                    .findDriverUserTokenByVehicleToken(token)
                    .map(driverUserToken -> driverUserToken.equals(callerUid)))
        .orElse(false);
  }
}
