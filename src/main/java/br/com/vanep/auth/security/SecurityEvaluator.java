package br.com.vanep.auth.security;

import br.com.vanep.driver.DriverRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("sec")
public class SecurityEvaluator {

  private final DriverRepository driverRepository;

  public SecurityEvaluator(DriverRepository driverRepository) {
    this.driverRepository = driverRepository;
  }

  /** Verifica se o usuário autenticado é o proprietário do perfil de motorista (driver). */
  public boolean isDriverOwner(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            uid ->
                driverRepository
                    .findUserTokenByDriverToken(token)
                    .map(driverUserToken -> driverUserToken.equals(uid)))
        .orElse(false);
  }
}
