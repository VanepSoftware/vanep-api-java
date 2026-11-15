package br.com.vanep.client.repository;

import br.com.vanep.client.model.ClientModel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<ClientModel, Long> {
  Optional<ClientModel> findByToken(String token);

  Optional<ClientModel> findByUserId(Long userId);

  // Only id and token, so a list of many clients never loads each client's EAGER user (rule 17).
  @Query(
      "select client.id as id, client.token as token from ClientModel client"
          + " where client.id in :ids")
  List<IdAndToken> findTokensByIdIn(@Param("ids") Collection<Long> ids);

  interface IdAndToken {
    Long getId();

    String getToken();
  }
}
