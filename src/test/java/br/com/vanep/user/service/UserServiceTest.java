package br.com.vanep.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import br.com.vanep.user.Gender;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.model.UserModel;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository users;
  @Mock private MessageSource messages;

  private UserService service;

  @BeforeEach
  void setUp() {
    service = new UserService(users, messages);
    lenient()
        .when(
            messages.getMessage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void requireByTokenReturnsUserWhenFound() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    assertThat(service.requireByToken("uid-1")).isSameAs(user);
  }

  @Test
  void requireByTokenThrows404WhenMissing() {
    when(users.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void requireByTokenAndTypeReturnsUserWhenTypeMatches() {
    UserModel user = userWithToken("uid-1", UserType.DRIVER);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    assertThat(service.requireByTokenAndType("uid-1", UserType.DRIVER)).isSameAs(user);
  }

  @Test
  void requireByTokenAndTypeThrows403WhenTypeMismatch() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.requireByTokenAndType("uid-1", UserType.DRIVER))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(403);
  }

  @Test
  void getMeIncludesBirthDateAndGender() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    user.setBirthDate(LocalDate.of(1990, 5, 15));
    user.setGender(Gender.FEMALE);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    UserMeResponseDTO me = service.getMe("uid-1");

    assertThat(me.birthDate()).isEqualTo(LocalDate.of(1990, 5, 15));
    assertThat(me.gender()).isEqualTo(Gender.FEMALE);
    assertThat(me.token()).isEqualTo("uid-1");
    assertThat(me.type()).isEqualTo("CLIENT");
  }

  @Test
  void getMeAllowsNullBirthDateAndGender() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    UserMeResponseDTO me = service.getMe("uid-1");

    assertThat(me.birthDate()).isNull();
    assertThat(me.gender()).isNull();
  }

  private static UserModel userWithToken(String token, UserType type) {
    UserModel user = new UserModel();
    user.setToken(token);
    user.setType(type);
    user.setName("Test User");
    user.setEmail("test@vanep.com");
    user.setDocument("12345678901");
    user.setPhone("11999999999");
    return user;
  }
}
