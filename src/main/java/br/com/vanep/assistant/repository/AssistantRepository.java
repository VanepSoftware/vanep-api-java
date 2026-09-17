package br.com.vanep.assistant.repository;

import br.com.vanep.assistant.model.AssistantModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantRepository extends JpaRepository<AssistantModel, Long> {

  Optional<AssistantModel> findByToken(String token);

  Optional<AssistantModel> findByUserId(Long userId);

  List<AssistantModel> findByDriverId(Long driverId);
}
