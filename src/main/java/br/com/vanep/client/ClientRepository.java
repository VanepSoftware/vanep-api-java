package br.com.vanep.client;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

  Optional<Client> findByToken(String token);

  Optional<Client> findByUserId(Long userId);
}
