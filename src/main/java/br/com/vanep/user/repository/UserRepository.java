package br.com.vanep.user.repository;

import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserModel, Long> {

  Optional<UserModel> findByEmail(String email);

  Optional<UserModel> findByToken(String token);

  boolean existsByEmail(String email);

  boolean existsByDocument(String document);

  List<UserModel> findByTypeAndRoleIdIsNull(UserType type);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "UPDATE users SET password = :upgraded"
              + " WHERE email = :email AND password = :current AND deleted_at IS NULL",
      nativeQuery = true)
  int upgradePasswordIfCurrent(
      @Param("email") String email,
      @Param("current") @Nullable String current,
      @Param("upgraded") String upgraded);
}
