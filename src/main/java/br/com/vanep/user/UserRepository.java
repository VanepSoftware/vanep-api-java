package br.com.vanep.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByDocument(String document);

  List<User> findByTypeAndRoleIdIsNull(UserType type);
}
