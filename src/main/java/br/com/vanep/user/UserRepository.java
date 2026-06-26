package br.com.vanep.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmailAndDeletedAtIsNull(String email);

  boolean existsByEmail(String email);
}
