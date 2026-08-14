package br.com.vanep.auth.verification;

import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationTokenModel, Long> {

  Optional<EmailVerificationTokenModel> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "update EmailVerificationTokenModel t set t.consumedAt = :now "
          + "where t.userId = :userId and t.consumedAt is null")
  void consumeAllActive(@Param("userId") Long userId, @Param("now") Instant now);
}
