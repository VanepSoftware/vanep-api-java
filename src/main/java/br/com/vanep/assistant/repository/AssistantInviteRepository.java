package br.com.vanep.assistant.repository;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.model.AssistantInviteModel;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssistantInviteRepository extends JpaRepository<AssistantInviteModel, Long> {

  Optional<AssistantInviteModel> findByToken(String token);

  Optional<AssistantInviteModel> findByAssistantIdAndStatus(
      Long assistantId, AssistantInviteStatus status);

  boolean existsByDriverIdAndAssistantIdAndStatusAndRespondedAtGreaterThanEqual(
      Long driverId, Long assistantId, AssistantInviteStatus status, Instant respondedAtSince);

  Optional<AssistantInviteModel> findByDriverIdAndAssistantIdAndStatus(
      Long driverId, Long assistantId, AssistantInviteStatus status);

  /**
   * Drivers the assistant once worked with: distinct drivers of accepted invites, leaving out
   * {@code currentDriverId} (pass -1 when the assistant has no driver now).
   */
  @Query(
      """
      select count(distinct invite.driver.id) from AssistantInviteModel invite
      where invite.assistant.id = :assistantId
        and invite.status = br.com.vanep.assistant.enums.AssistantInviteStatus.ACCEPTED
        and invite.driver.id <> :currentDriverId
      """)
  long countFormerDrivers(Long assistantId, Long currentDriverId);
}
