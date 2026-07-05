package br.com.vanep.client.repository;

import br.com.vanep.client.model.ClientModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<ClientModel, Long> {

  Optional<ClientModel> findByToken(String token);

  Optional<ClientModel> findByUserId(Long userId);
}
