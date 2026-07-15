package br.com.vanep.state.repository;

import br.com.vanep.state.model.StateModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateRepository extends JpaRepository<StateModel, Long> {

  Optional<StateModel> findByToken(String token);

  Optional<StateModel> findByUf(String uf);

  boolean existsByUf(String uf);
}
