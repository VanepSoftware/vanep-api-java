package br.com.vanep.rolepermission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.rolepermission.model.RolePermissionModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RolePermissionRepositoryTest {

  @Autowired private RolePermissionRepository repository;

  @Test
  @Transactional
  void permissionsRoundTripAsJsonList() {
    RolePermissionModel bundle = new RolePermissionModel();
    bundle.setName("Test Bundle");
    bundle.setPermissions(List.of("list_roles", "show_role"));
    RolePermissionModel saved = repository.saveAndFlush(bundle);
    repository.findById(saved.getId());

    RolePermissionModel reloaded = repository.findByToken(saved.getToken()).orElseThrow();

    assertThat(reloaded.getPermissions()).containsExactly("list_roles", "show_role");
  }

  @Test
  void softDeletedBundleIsAbsentFromDefaultListing() {
    RolePermissionModel bundle = new RolePermissionModel();
    bundle.setName("To Delete");
    bundle.setPermissions(List.of("list_roles"));
    RolePermissionModel saved = repository.save(bundle);

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }
}
