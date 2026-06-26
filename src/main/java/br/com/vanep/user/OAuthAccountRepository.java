package br.com.vanep.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

  Optional<OAuthAccount> findByProviderAndProviderUid(AuthProvider provider, String providerUid);
}
