package br.com.vanep.seed;

import br.com.vanep.config.PasswordHasher;
import br.com.vanep.entity.UserEntity;
import br.com.vanep.enums.UserTypeEnum;
import br.com.vanep.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "docker"})
public class UserSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);
  private static final String TEST_PASSWORD = "password123";

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;

  public UserSeeder(UserRepository userRepository, PasswordHasher passwordHasher) {
    this.userRepository = userRepository;
    this.passwordHasher = passwordHasher;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepository.count() > 0) {
      log.debug("Users already seeded, skipping.");
      return;
    }

    String encodedPassword = passwordHasher.encode(TEST_PASSWORD);
    List<UserEntity> users =
        List.of(
            user(
                UserTypeEnum.CLIENT,
                "João Silva",
                "joao.silva@vanep.test",
                "joao_silva",
                "12345678901",
                "11999990001",
                true,
                encodedPassword),
            user(
                UserTypeEnum.DRIVER,
                "Maria Souza",
                "maria.souza@vanep.test",
                "maria_souza",
                "23456789012",
                "11999990002",
                true,
                encodedPassword),
            user(
                UserTypeEnum.ADMIN,
                "Admin Vanep",
                "admin@vanep.test",
                "admin",
                "34567890123",
                "11999990003",
                true,
                encodedPassword),
            user(
                UserTypeEnum.CLIENT,
                "Pedro Costa",
                "pedro.costa@vanep.test",
                "pedro_costa",
                "45678901234",
                "11999990004",
                false,
                encodedPassword),
            user(
                UserTypeEnum.DRIVER,
                "Ana Lima",
                "ana.lima@vanep.test",
                "ana_lima",
                "56789012345",
                "11999990005",
                false,
                encodedPassword));

    userRepository.saveAll(users);
    log.info("Seeded {} test users (password: {}).", users.size(), TEST_PASSWORD);
  }

  private static UserEntity user(
      UserTypeEnum type,
      String name,
      String email,
      String username,
      String cpf,
      String phone,
      boolean verified,
      String encodedPassword) {
    UserEntity user = new UserEntity();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setUsername(username);
    user.setCpf(cpf);
    user.setPhone(phone);
    user.setVerified(verified);
    user.setPassword(encodedPassword);
    return user;
  }
}
