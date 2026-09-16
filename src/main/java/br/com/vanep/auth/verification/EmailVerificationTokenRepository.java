package br.com.vanep.auth.verification;

import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationTokenModel, Long> {

  Optional<EmailVerificationTokenModel> findByTokenHash(String tokenHash);

  boolean existsByUserIdAndConsumedAtIsNullAndExpiresAtAfter(Long userId, Instant now);

  long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

  Optional<EmailVerificationTokenModel> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select t from EmailVerificationTokenModel t where t.userId = :userId "
          + "and t.consumedAt is null and t.expiresAt > :now order by t.createdAt desc limit 1")
  Optional<EmailVerificationTokenModel> lockLatestActive(
      @Param("userId") Long userId, @Param("now") Instant now);

  @Modifying
  @Query(
      "update EmailVerificationTokenModel t set t.consumedAt = :now "
          + "where t.userId = :userId and t.consumedAt is null")
  void consumeAllActive(@Param("userId") Long userId, @Param("now") Instant now);
}
