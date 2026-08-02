package br.com.vanep.user;

import br.com.vanep.user.model.UserModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserModel, Long> {

  Optional<UserModel> findByEmail(String email);

  Optional<UserModel> findByToken(String token);

  boolean existsByEmail(String email);

  boolean existsByDocument(String document);

  List<UserModel> findByTypeAndRoleIdIsNull(UserType type);
}
