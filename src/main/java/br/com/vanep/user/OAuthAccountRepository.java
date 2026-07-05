package br.com.vanep.user;

import br.com.vanep.user.model.OAuthAccountModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccountModel, Long> {

  Optional<OAuthAccountModel> findByProviderAndProviderUid(
      AuthProvider provider, String providerUid);
}
