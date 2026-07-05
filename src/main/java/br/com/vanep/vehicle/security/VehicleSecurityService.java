package br.com.vanep.vehicle.security;

import br.com.vanep.vehicle.repository.VehicleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service("vehicleSecurity")
public class VehicleSecurityService {

  private final VehicleRepository repository;

  public VehicleSecurityService(VehicleRepository repository) {
    this.repository = repository;
  }

  public boolean isOwner(String token, Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) return false;
    String callerUid = jwtAuth.getToken().getClaim("uid");
    return repository
        .findDriverUserTokenByVehicleToken(token)
        .map(driverUserToken -> driverUserToken.equals(callerUid))
        .orElse(false);
  }
}
