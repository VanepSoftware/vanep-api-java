package br.com.vanep.auth.password;

import br.com.vanep.auth.password.model.PasswordResetTokenModel;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenModel, Long> {

  Optional<PasswordResetTokenModel> findByTokenHash(String tokenHash);

  long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

  Optional<PasswordResetTokenModel> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select t from PasswordResetTokenModel t where t.userId = :userId "
          + "and t.consumedAt is null and t.expiresAt > :now order by t.createdAt desc limit 1")
  Optional<PasswordResetTokenModel> lockLatestActive(
      @Param("userId") Long userId, @Param("now") Instant now);

  @Modifying
  @Query(
      "update PasswordResetTokenModel t set t.consumedAt = :now "
          + "where t.userId = :userId and t.consumedAt is null")
  void consumeAllActive(@Param("userId") Long userId, @Param("now") Instant now);
}
