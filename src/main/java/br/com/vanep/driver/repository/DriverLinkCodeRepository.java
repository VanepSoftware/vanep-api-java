package br.com.vanep.driver.repository;

import br.com.vanep.driver.DriverLinkCodeStatus;
import br.com.vanep.driver.model.DriverLinkCodeModel;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverLinkCodeRepository extends JpaRepository<DriverLinkCodeModel, Long> {

  Optional<DriverLinkCodeModel> findByCodeHash(String codeHash);

  Optional<DriverLinkCodeModel> findByDriverIdAndStatus(Long driverId, DriverLinkCodeStatus status);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE DriverLinkCodeModel c
      SET c.status = br.com.vanep.driver.DriverLinkCodeStatus.CONSUMED,
          c.consumedAt = :now,
          c.consumedByAssistantId = :assistantId
      WHERE c.codeHash = :codeHash
        AND c.status = br.com.vanep.driver.DriverLinkCodeStatus.ACTIVE
        AND c.expiresAt > :now
      """)
  int consumeIfActive(
      @Param("codeHash") String codeHash,
      @Param("assistantId") Long assistantId,
      @Param("now") Instant now);
}
