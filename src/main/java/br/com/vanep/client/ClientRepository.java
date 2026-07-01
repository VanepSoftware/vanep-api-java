package br.com.vanep.client;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, Long> {

  @Query("SELECT c FROM Client c WHERE c.user.id = :userId")
  Optional<Client> findByUserId(@Param("userId") Long userId);
}
