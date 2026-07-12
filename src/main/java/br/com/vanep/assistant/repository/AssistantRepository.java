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

  List<AssistantModel> findByDriverId(Long driverId);

  @Query("SELECT a.driver.user.token FROM AssistantModel a WHERE a.token = :token")
  Optional<String> findDriverUserTokenByAssistantToken(@Param("token") String token);

  @Query("SELECT a FROM AssistantModel a JOIN a.user u WHERE u.token = :userToken")
  Optional<AssistantModel> findByUserToken(@Param("userToken") String userToken);
}
