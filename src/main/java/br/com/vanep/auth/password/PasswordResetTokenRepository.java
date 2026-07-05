package br.com.vanep.auth.password;

import br.com.vanep.auth.password.model.PasswordResetTokenModel;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenModel, Long> {

  Optional<PasswordResetTokenModel> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "update PasswordResetTokenModel t set t.consumedAt = :now "
          + "where t.userId = :userId and t.consumedAt is null")
  void consumeAllActive(@Param("userId") Long userId, @Param("now") Instant now);
}
