package br.com.vanep.state.repository;

import br.com.vanep.state.model.StateModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateRepository extends JpaRepository<StateModel, Long> {
  Optional<StateModel> findByToken(String token);

  Optional<StateModel> findByUf(String uf);

  Optional<StateModel> findByCountryIdAndUfIgnoreCase(Long countryId, String uf);

  Optional<StateModel> findByCountryIdAndNormalizedName(Long countryId, String normalizedName);

  boolean existsByUf(String uf);
}
