package br.com.vanep.auth.password;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /** Invalida tokens ainda válidos de um usuário (ao emitir um novo ou após o reset). */
  @Modifying
  @Query(
      "update PasswordResetToken t set t.consumedAt = :now "
          + "where t.userId = :userId and t.consumedAt is null")
  void consumeAllActive(@Param("userId") Long userId, @Param("now") Instant now);
}
