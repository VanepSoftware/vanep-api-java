package br.com.vanep.driver;

import br.com.vanep.driver.model.DriverModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<DriverModel, Long> {

  Optional<DriverModel> findByUserId(Long userId);

  Optional<DriverModel> findByToken(String token);
}
