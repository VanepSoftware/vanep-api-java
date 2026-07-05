package br.com.vanep.role.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RoleRepositoryTest {

  @Autowired private RoleRepository repository;

  @Test
  void findByRoleNameLocatesRoleRegardlessOfCurrentDisplayName() {
    RoleModel role = new RoleModel();
    role.setName("Administrador");
    role.setRoleName(RoleName.ADMIN);
    repository.save(role);

    role.setName("Renamed Admin");
    repository.save(role);

    RoleModel found = repository.findByRoleName(RoleName.ADMIN).orElseThrow();

    assertThat(found.getName()).isEqualTo("Renamed Admin");
  }

  @Test
  void findByRoleNameReturnsEmptyWhenUntagged() {
    RoleModel role = new RoleModel();
    role.setName("Plain Role");
    repository.save(role);

    assertThat(repository.findByRoleName(RoleName.DRIVER)).isEmpty();
  }
}
