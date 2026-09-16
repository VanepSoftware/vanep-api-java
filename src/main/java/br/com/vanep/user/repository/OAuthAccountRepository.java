package br.com.vanep.user.repository;

import br.com.vanep.user.enums.AuthProvider;
import br.com.vanep.user.model.OAuthAccountModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccountModel, Long> {

  Optional<OAuthAccountModel> findByProviderAndProviderUid(
      AuthProvider provider, String providerUid);

  /**
   * Native on purpose: a deactivated ({@code @SoftDelete}) owner makes the mapped association
   * unusable — loading it fails, and navigating it in HQL filters the row out, which would look
   * like "never linked" instead of "account disabled". The table itself has no soft delete.
   */
  @Query(
      value =
          "select user_id from oauth_account where provider = :provider "
              + "and provider_uid = :providerUid",
      nativeQuery = true)
  Optional<Long> findLinkedUserId(
      @Param("provider") String provider, @Param("providerUid") String providerUid);
}
