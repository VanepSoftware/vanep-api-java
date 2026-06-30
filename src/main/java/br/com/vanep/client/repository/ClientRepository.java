package br.com.vanep.client.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import br.com.vanep.client.model.ClientModel;

public interface ClientRepository extends JpaRepository<ClientModel, Long> {

  boolean existsByUserId(Long userId);
}