package br.com.vanep.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.config.PasswordHasher;
import br.com.vanep.users.dto.UserPayloadDto;
import br.com.vanep.users.dto.UserResponseDto;
import br.com.vanep.users.entity.UserEntity;
import br.com.vanep.users.enums.UserTypeEnum;
import br.com.vanep.users.mapper.UserMapper;
import br.com.vanep.users.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordHasher passwordHasher;
  @Mock private UserMapper userMapper;

  @InjectMocks private UserService userService;

  private UserPayloadDto payload;

  @BeforeEach
  void setUp() {
    payload =
        new UserPayloadDto(
            "Nome",
            UserTypeEnum.CLIENT,
            "a@b.com",
            "user1",
            "raw-secret",
            "12345678901",
            "11999990000");
  }

  @Test
  void create_hashesPasswordAndSaves() {
    UserEntity saved = new UserEntity();
    saved.setToken("t1");
    when(passwordHasher.encode("raw-secret")).thenReturn("ENC");
    when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userMapper.toResponse(any(UserEntity.class)))
        .thenReturn(
            new UserResponseDto(
                "t1",
                UserTypeEnum.CLIENT,
                "Nome",
                "a@b.com",
                "user1",
                "12345678901",
                false,
                "11999990000"));

    UserResponseDto result = userService.create(payload);

    assertThat(result.token()).isEqualTo("t1");
    verify(passwordHasher).encode("raw-secret");
    verify(userRepository).save(any(UserEntity.class));
  }

  @Test
  void findAll_returnsMappedList() {
    UserEntity e = new UserEntity();
    when(userRepository.findAll()).thenReturn(List.of(e));
    when(userMapper.toResponse(e)).thenReturn(sampleResponse("tok"));

    assertThat(userService.findAll()).hasSize(1);
  }

  @Test
  void findByToken_whenMissing_throwsNotFound() {
    when(userRepository.findByToken("x")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.findByToken("x"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void findByToken_whenPresent_returnsDto() {
    UserEntity e = new UserEntity();
    when(userRepository.findByToken("tok")).thenReturn(Optional.of(e));
    when(userMapper.toResponse(e)).thenReturn(sampleResponse("tok"));

    assertThat(userService.findByToken("tok").token()).isEqualTo("tok");
  }

  @Test
  void update_withoutPasswordChange_doesNotReencode() {
    UserEntity existing = new UserEntity();
    existing.setPassword("OLD");
    when(userRepository.findByToken("tok")).thenReturn(Optional.of(existing));
    when(userRepository.save(existing)).thenReturn(existing);
    UserPayloadDto noPwd =
        new UserPayloadDto("N2", UserTypeEnum.DRIVER, "b@b.com", "u2", null, "98765432100", null);
    when(userMapper.toResponse(existing)).thenReturn(sampleResponse("tok"));

    userService.update("tok", noPwd);

    verify(passwordHasher, never()).encode(any());
    verify(userRepository).save(existing);
  }

  @Test
  void update_withBlankPassword_doesNotReencode() {
    UserEntity existing = new UserEntity();
    existing.setPassword("OLD");
    when(userRepository.findByToken("tok")).thenReturn(Optional.of(existing));
    when(userRepository.save(existing)).thenReturn(existing);
    UserPayloadDto blankPwd =
        new UserPayloadDto("N2", UserTypeEnum.DRIVER, "b@b.com", "u2", "   ", "98765432100", null);
    when(userMapper.toResponse(existing)).thenReturn(sampleResponse("tok"));

    userService.update("tok", blankPwd);

    verify(passwordHasher, never()).encode(any());
  }

  @Test
  void update_withNewPassword_reencodes() {
    UserEntity existing = new UserEntity();
    existing.setPassword("OLD");
    when(userRepository.findByToken("tok")).thenReturn(Optional.of(existing));
    when(userRepository.save(existing)).thenReturn(existing);
    when(passwordHasher.encode("newpass")).thenReturn("ENC2");
    UserPayloadDto withPwd =
        new UserPayloadDto(
            "N2", UserTypeEnum.ADMIN, "b@b.com", "u2", "newpass", "98765432100", null);
    when(userMapper.toResponse(existing)).thenReturn(sampleResponse("tok"));

    userService.update("tok", withPwd);

    verify(passwordHasher).encode("newpass");
    assertThat(existing.getPassword()).isEqualTo("ENC2");
  }

  @Test
  void delete_removesByToken() {
    UserEntity existing = new UserEntity();
    when(userRepository.findByToken("tok")).thenReturn(Optional.of(existing));

    userService.delete("tok");

    verify(userRepository).delete(existing);
  }

  private static UserResponseDto sampleResponse(String token) {
    return new UserResponseDto(token, UserTypeEnum.CLIENT, "N", "a@a.com", "u", "1", true, null);
  }
}
