package br.com.vanep.auth.security;

import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
import br.com.vanep.vehicle.repository.VehicleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("sec")
public class SecurityEvaluator {

  private final DriverRepository driverRepository;
  private final ClientRepository clientRepository;
  private final VehicleRepository vehicleRepository;
  private final DriverCnhRepository cnhRepository;
  private final DriverRatingRepository driverRatingRepository;
  private final DriverDocumentRepository driverDocumentRepository;

  public SecurityEvaluator(
      DriverRepository driverRepository,
      ClientRepository clientRepository,
      VehicleRepository vehicleRepository,
      DriverCnhRepository cnhRepository,
      DriverRatingRepository driverRatingRepository,
      DriverDocumentRepository driverDocumentRepository) {
    this.driverRepository = driverRepository;
    this.clientRepository = clientRepository;
    this.vehicleRepository = vehicleRepository;
    this.cnhRepository = cnhRepository;
    this.driverRatingRepository = driverRatingRepository;
    this.driverDocumentRepository = driverDocumentRepository;
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

  public boolean isCnhOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                cnhRepository
                    .findDriverUserTokenByCnhToken(token)
                    .map(driverUserToken -> driverUserToken.equals(uid)))
        .orElse(false);
  }

  public boolean isDriverRatingOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                driverRatingRepository
                    .findClientUserTokenByRatingToken(token)
                    .map(clientUserToken -> clientUserToken.equals(uid)))
        .orElse(false);
  }

  public boolean isDriverDocumentOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                driverDocumentRepository
                    .findDriverUserTokenByDocumentToken(token)
                    .map(driverUserToken -> driverUserToken.equals(uid)))
        .orElse(false);
  }
}
