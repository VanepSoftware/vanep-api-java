package br.com.vanep.client.security;

import br.com.vanep.client.ClientRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service("clientSecurity")
public class ClientSecurityService {

  private final ClientRepository repository;

  public ClientSecurityService(ClientRepository repository) {
    this.repository = repository;
  }

  public boolean isOwner(String token, Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) return false;
    String callerUid = jwtAuth.getToken().getClaim("uid");
    return repository
        .findByToken(token)
        .map(c -> c.getUser().getToken().equals(callerUid))
        .orElse(false);
  }
}
