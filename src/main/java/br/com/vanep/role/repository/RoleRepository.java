package br.com.vanep.role.repository;

import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RoleRepository extends JpaRepository<RoleModel, Long> {

  Optional<RoleModel> findByToken(String token);

  Optional<RoleModel> findByRoleName(RoleName roleName);

  boolean existsByName(String name);

  @Query(
      value = "SELECT * FROM roles WHERE token = :token AND deleted_at IS NOT NULL",
      nativeQuery = true)
  Optional<RoleModel> findDeletedByToken(String token);

  @Modifying
  @Transactional
  @Query(value = "UPDATE roles SET deleted_at = NULL WHERE token = :token", nativeQuery = true)
  void restore(String token);
}
