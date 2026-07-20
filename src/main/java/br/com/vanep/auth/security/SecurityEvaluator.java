package br.com.vanep.auth.security;

import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.vehicle.repository.VehicleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("sec")
public class SecurityEvaluator {

  private final DriverRepository driverRepository;
  private final ClientRepository clientRepository;
  private final VehicleRepository vehicleRepository;

  public SecurityEvaluator(
      DriverRepository driverRepository,
      ClientRepository clientRepository,
      VehicleRepository vehicleRepository) {
    this.driverRepository = driverRepository;
    this.clientRepository = clientRepository;
    this.vehicleRepository = vehicleRepository;
  }

  public boolean isDriverOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                driverRepository
                    .findUserTokenByDriverToken(token)
                    .map(driverUserToken -> driverUserToken.equals(uid)))
        .orElse(false);
  }

  public boolean isClientOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                clientRepository
                    .findByToken(token)
                    .map(client -> client.getUser().getToken().equals(uid)))
        .orElse(false);
  }

  public boolean isVehicleOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                vehicleRepository
                    .findDriverUserTokenByVehicleToken(token)
                    .map(driverUserToken -> driverUserToken.equals(uid)))
        .orElse(false);
  }
}
