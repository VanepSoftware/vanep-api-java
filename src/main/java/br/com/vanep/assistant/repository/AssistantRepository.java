package br.com.vanep.assistant.repository;

import br.com.vanep.assistant.model.AssistantModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantRepository extends JpaRepository<AssistantModel, Long> {

  Optional<AssistantModel> findByToken(String token);

  Optional<AssistantModel> findByUserId(Long userId);

  // Each assistant has its own user and photo, so they are fetched with the list (rule 17).
  @EntityGraph(attributePaths = {"user", "driver.user", "photo"})
  List<AssistantModel> findByDriverId(Long driverId);
}
