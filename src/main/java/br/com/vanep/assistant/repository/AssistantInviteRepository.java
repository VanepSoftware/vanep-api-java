package br.com.vanep.assistant.repository;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.model.AssistantInviteModel;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantInviteRepository extends JpaRepository<AssistantInviteModel, Long> {

  Optional<AssistantInviteModel> findByToken(String token);

  Optional<AssistantInviteModel> findByAssistantIdAndStatus(
      Long assistantId, AssistantInviteStatus status);

  boolean existsByDriverIdAndAssistantIdAndStatusAndRespondedAtGreaterThanEqual(
      Long driverId, Long assistantId, AssistantInviteStatus status, Instant respondedAtSince);

  Optional<AssistantInviteModel> findByDriverIdAndAssistantIdAndStatus(
      Long driverId, Long assistantId, AssistantInviteStatus status);
}
