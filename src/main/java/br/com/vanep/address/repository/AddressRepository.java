package br.com.vanep.address.repository;

import br.com.vanep.address.model.AddressModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<AddressModel, Long> {

  Optional<AddressModel> findByToken(String token);

  boolean existsByZipCodeAndNumber(String zipCode, String number);

  @Modifying
  @Query(value = "UPDATE address SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  int restoreByToken(@Param("token") String token);

  @Query(
      value = "SELECT count(*) > 0 FROM address WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean existsDeletedByToken(@Param("token") String token);
}
