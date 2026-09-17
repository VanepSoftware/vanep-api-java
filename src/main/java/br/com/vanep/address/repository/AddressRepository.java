package br.com.vanep.address.repository;

import br.com.vanep.address.model.AddressModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<AddressModel, Long> {

  Optional<AddressModel> findByToken(String token);
}
