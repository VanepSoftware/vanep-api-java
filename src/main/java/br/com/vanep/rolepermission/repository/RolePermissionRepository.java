package br.com.vanep.rolepermission.repository;

import br.com.vanep.rolepermission.model.RolePermissionModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermissionModel, Long> {

  Optional<RolePermissionModel> findByToken(String token);

  boolean existsByName(String name);
}
