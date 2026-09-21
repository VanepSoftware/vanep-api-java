package br.com.vanep.auth.security;

import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientrating.repository.ClientRatingRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.media.service.MediaOwnerResolver;
import br.com.vanep.trip.repository.TripRepository;
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
  private final ClientRatingRepository clientRatingRepository;
  private final DriverDocumentRepository driverDocumentRepository;
  private final TripRepository tripRepository;
  private final MediaFileRepository mediaFileRepository;
  private final MediaOwnerResolver mediaOwnerResolver;

  public SecurityEvaluator(
      DriverRepository driverRepository,
      ClientRepository clientRepository,
      VehicleRepository vehicleRepository,
      DriverCnhRepository cnhRepository,
      DriverRatingRepository driverRatingRepository,
      ClientRatingRepository clientRatingRepository,
      DriverDocumentRepository driverDocumentRepository,
      TripRepository tripRepository,
      MediaFileRepository mediaFileRepository,
      MediaOwnerResolver mediaOwnerResolver) {
    this.driverRepository = driverRepository;
    this.clientRepository = clientRepository;
    this.vehicleRepository = vehicleRepository;
    this.cnhRepository = cnhRepository;
    this.driverRatingRepository = driverRatingRepository;
    this.clientRatingRepository = clientRatingRepository;
    this.driverDocumentRepository = driverDocumentRepository;
    this.tripRepository = tripRepository;
    this.mediaFileRepository = mediaFileRepository;
    this.mediaOwnerResolver = mediaOwnerResolver;
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

  public boolean isClientRatingOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                clientRatingRepository
                    .findDriverUserTokenByRatingToken(token)
                    .map(driverUserToken -> driverUserToken.equals(uid)))
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

  public boolean isTripOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                tripRepository
                    .findDriverUserTokenByTripToken(token)
                    .map(driverUserToken -> driverUserToken.equals(uid)))
        .orElse(false);
  }

  public boolean isMediaOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                mediaFileRepository
                    .findByToken(token)
                    .flatMap(
                        media ->
                            mediaOwnerResolver.resolveOwnerUserToken(
                                media.getOwnerType(), media.getOwnerId()))
                    .map(ownerUid -> ownerUid.equals(uid)))
        .orElse(false);
  }
}
