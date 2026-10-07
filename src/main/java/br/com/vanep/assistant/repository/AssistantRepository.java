package br.com.vanep.assistant.repository;

import br.com.vanep.assistant.model.AssistantModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantRepository extends JpaRepository<AssistantModel, Long> {

  Optional<AssistantModel> findByToken(String token);

  Optional<AssistantModel> findByUserId(Long userId);

  @Query(
      """
      select assistant from AssistantModel assistant
      join fetch assistant.user
      left join fetch assistant.driver driver
      left join fetch driver.user
      where assistant.user.token = :uid
      """)
  Optional<AssistantModel> findByUserToken(@Param("uid") String uid);

  List<AssistantModel> findByDriverId(Long driverId);
}
