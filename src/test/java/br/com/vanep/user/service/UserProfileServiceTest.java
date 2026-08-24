package br.com.vanep.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.verification.EmailVerificationService;
import br.com.vanep.user.UserProfileFieldLimits;
import br.com.vanep.user.dto.UserEmailChangeRequestDTO;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.dto.UserProfileUpdateRequestDTO;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.enums.ProfileErrorCode;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.exception.ProfileBadRequestException;
import br.com.vanep.user.exception.ProfileCooldownException;
import br.com.vanep.user.exception.ProfileEmailDuplicateException;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.policy.UserProfileChangePolicy;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

  @Mock private UserService userService;
  @Mock private UserRepository users;
  @Mock private MessageSource messages;
  @Mock private EmailVerificationService emailVerification;

  private UserProfileChangePolicy policy;
  private UserProfileService service;

  @BeforeEach
  void setUp() {
    policy = new UserProfileChangePolicy(30);
    service = new UserProfileService(userService, users, policy, messages, emailVerification);
    lenient().when(messages.getMessage(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(users.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));
    lenient()
        .when(userService.toMeResponse(any(UserModel.class)))
        .thenAnswer(
            inv -> {
              UserModel u = inv.getArgument(0);
              return new UserMeResponseDTO(
                  u.getToken(),
                  u.getName(),
                  u.getPhone(),
                  u.getEmail(),
                  u.getDocument(),
                  u.getBirthDate(),
                  u.getGender(),
                  u.getType().name(),
                  null,
                  null,
                  null,
                  null,
                  null);
            });
  }

  @Test
  void absentFieldsAreNoOp() {
    UserModel user = sampleUser();
    Instant previousNameChange = Instant.parse("2026-01-01T00:00:00Z");
    user.setLastNameChangeAt(previousNameChange);
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

    UserMeResponseDTO result = service.patchMe("uid-1", request);

    assertThat(result.name()).isEqualTo("Test User");
    assertThat(result.phone()).isEqualTo("11999999999");
    assertThat(result.gender()).isEqualTo(Gender.MALE);
    assertThat(user.getLastNameChangeAt()).isEqualTo(previousNameChange);
    verify(users, never()).save(any());
  }

  @Test
  void explicitNullNameReturns400() {
    UserModel user = sampleUser();
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.of(null), JsonNullable.undefined(), JsonNullable.undefined());

    assertThatThrownBy(() -> service.patchMe("uid-1", request))
        .isInstanceOf(ProfileBadRequestException.class)
        .satisfies(
            ex -> {
              ProfileBadRequestException error = (ProfileBadRequestException) ex;
              assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.getCode()).isEqualTo(ProfileErrorCode.FIELD_NULL);
              assertThat(error.getField()).isEqualTo("name");
            });
    verify(users, never()).save(any());
  }

  @Test
  void explicitNullPhoneReturns400() {
    UserModel user = sampleUser();
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.undefined(), JsonNullable.of(null), JsonNullable.undefined());

    assertThatThrownBy(() -> service.patchMe("uid-1", request))
        .isInstanceOf(ProfileBadRequestException.class)
        .satisfies(
            ex -> {
              ProfileBadRequestException error = (ProfileBadRequestException) ex;
              assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.getCode()).isEqualTo(ProfileErrorCode.FIELD_NULL);
              assertThat(error.getField()).isEqualTo("phone");
            });
  }

  @Test
  void explicitNullGenderReturns400() {
    UserModel user = sampleUser();
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.of(null));

    assertThatThrownBy(() -> service.patchMe("uid-1", request))
        .isInstanceOf(ProfileBadRequestException.class)
        .satisfies(
            ex -> {
              ProfileBadRequestException error = (ProfileBadRequestException) ex;
              assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.getCode()).isEqualTo(ProfileErrorCode.FIELD_NULL);
              assertThat(error.getField()).isEqualTo("gender");
            });
  }

  @Test
  void blankPhoneReturns400() {
    UserModel user = sampleUser();
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.undefined(), JsonNullable.of(""), JsonNullable.undefined());

    assertThatThrownBy(() -> service.patchMe("uid-1", request))
        .isInstanceOf(ProfileBadRequestException.class)
        .satisfies(
            ex -> {
              ProfileBadRequestException error = (ProfileBadRequestException) ex;
              assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.getCode()).isEqualTo(ProfileErrorCode.PHONE_BLANK);
              assertThat(error.getField()).isEqualTo("phone");
            });
    assertThat(user.getPhone()).isEqualTo("11999999999");
    verify(users, never()).save(any());
  }

  @Test
  void nameTooLongReturns400() {
    UserModel user = sampleUser();
    when(userService.requireByToken("uid-1")).thenReturn(user);

    String tooLong = "a".repeat(UserProfileFieldLimits.NAME_MAX + 1);
    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.of(tooLong), JsonNullable.undefined(), JsonNullable.undefined());

    assertThatThrownBy(() -> service.patchMe("uid-1", request))
        .isInstanceOf(ProfileBadRequestException.class)
        .satisfies(
            ex -> {
              ProfileBadRequestException error = (ProfileBadRequestException) ex;
              assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.getCode()).isEqualTo(ProfileErrorCode.NAME_TOO_LONG);
              assertThat(error.getField()).isEqualTo("name");
            });
    verify(users, never()).save(any());
  }

  @Test
  void phoneTooLongReturns400() {
    UserModel user = sampleUser();
    when(userService.requireByToken("uid-1")).thenReturn(user);

    String tooLong = "1".repeat(UserProfileFieldLimits.PHONE_MAX + 1);
    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.undefined(), JsonNullable.of(tooLong), JsonNullable.undefined());

    assertThatThrownBy(() -> service.patchMe("uid-1", request))
        .isInstanceOf(ProfileBadRequestException.class)
        .satisfies(
            ex -> {
              ProfileBadRequestException error = (ProfileBadRequestException) ex;
              assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.getCode()).isEqualTo(ProfileErrorCode.PHONE_TOO_LONG);
              assertThat(error.getField()).isEqualTo("phone");
            });
    verify(users, never()).save(any());
  }

  @Test
  void nameCooldownReturns409() {
    UserModel user = sampleUser();
    Instant lastChange = Instant.now().minus(5, ChronoUnit.DAYS);
    user.setLastNameChangeAt(lastChange);
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.of("New Name"), JsonNullable.undefined(), JsonNullable.undefined());

    assertThatThrownBy(() -> service.patchMe("uid-1", request))
        .isInstanceOf(ProfileCooldownException.class)
        .satisfies(
            ex -> {
              ProfileCooldownException pce = (ProfileCooldownException) ex;
              assertThat(pce.getCode()).isEqualTo(ProfileErrorCode.COOLDOWN);
              assertThat(pce.getField()).isEqualTo("name");
              assertThat(pce.getRetryAfter()).isEqualTo(lastChange.plus(30, ChronoUnit.DAYS));
              assertThat(pce.getMessage()).isEqualTo("user.profile.name.cooldown");
            });
    assertThat(user.getName()).isEqualTo("Test User");
    verify(users, never()).save(any());
  }

  @Test
  void genderAlwaysOkRegardlessOfCooldowns() {
    UserModel user = sampleUser();
    user.setLastNameChangeAt(Instant.now().minus(1, ChronoUnit.DAYS));
    user.setLastPhoneChangeAt(Instant.now().minus(1, ChronoUnit.DAYS));
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.of(Gender.FEMALE));

    UserMeResponseDTO result = service.patchMe("uid-1", request);

    assertThat(result.gender()).isEqualTo(Gender.FEMALE);
    ArgumentCaptor<UserModel> captor = ArgumentCaptor.forClass(UserModel.class);
    verify(users).save(captor.capture());
    assertThat(captor.getValue().getGender()).isEqualTo(Gender.FEMALE);
  }

  @Test
  void sameNameDoesNotBumpCooldown() {
    UserModel user = sampleUser();
    Instant previousNameChange = Instant.parse("2026-01-01T00:00:00Z");
    user.setLastNameChangeAt(previousNameChange);
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.of("Test User"), JsonNullable.undefined(), JsonNullable.undefined());

    UserMeResponseDTO result = service.patchMe("uid-1", request);

    assertThat(result.name()).isEqualTo("Test User");
    assertThat(user.getLastNameChangeAt()).isEqualTo(previousNameChange);
    verify(users, never()).save(any());
  }

  @Test
  void successfulNameAndPhoneUpdatePersistsAndBumpsCooldown() {
    UserModel user = sampleUser();
    LocalDate birthDate = LocalDate.of(1990, 5, 15);
    String document = "12345678901";
    user.setBirthDate(birthDate);
    user.setDocument(document);
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.of("Updated Name"),
            JsonNullable.of("11888888888"),
            JsonNullable.of(Gender.OTHER));

    UserMeResponseDTO result = service.patchMe("uid-1", request);

    assertThat(result.name()).isEqualTo("Updated Name");
    assertThat(result.phone()).isEqualTo("11888888888");
    assertThat(result.gender()).isEqualTo(Gender.OTHER);
    assertThat(result.document()).isEqualTo(document);
    assertThat(result.birthDate()).isEqualTo(birthDate);
    assertThat(user.getDocument()).isEqualTo(document);
    assertThat(user.getBirthDate()).isEqualTo(birthDate);
    assertThat(user.getLastNameChangeAt()).isNotNull();
    assertThat(user.getLastPhoneChangeAt()).isNotNull();
    verify(users).save(user);
  }

  @Test
  void phoneUpdateAllowedWhileNameCooldownActive() {
    UserModel user = sampleUser();
    user.setLastNameChangeAt(Instant.now().minus(5, ChronoUnit.DAYS));
    when(userService.requireByToken("uid-1")).thenReturn(user);

    UserProfileUpdateRequestDTO request =
        new UserProfileUpdateRequestDTO(
            JsonNullable.undefined(), JsonNullable.of("11777777777"), JsonNullable.undefined());

    UserMeResponseDTO result = service.patchMe("uid-1", request);

    assertThat(result.phone()).isEqualTo("11777777777");
    assertThat(user.getLastPhoneChangeAt()).isNotNull();
    verify(users).save(user);
  }

  @Test
  void requestEmailChangeSetsPendingWithoutTouchingEmailOrCooldown() {
    UserModel user = sampleUser();
    Instant previousEmailChange = Instant.parse("2025-01-01T00:00:00Z");
    user.setLastEmailChangeAt(previousEmailChange);
    user.setVerified(true);
    when(userService.requireByToken("uid-1")).thenReturn(user);
    when(users.existsByEmail("new@vanep.com")).thenReturn(false);

    service.requestEmailChange("uid-1", new UserEmailChangeRequestDTO("new@vanep.com"));

    assertThat(user.getPendingEmail()).isEqualTo("new@vanep.com");
    assertThat(user.getEmail()).isEqualTo("test@vanep.com");
    assertThat(user.getLastEmailChangeAt()).isEqualTo(previousEmailChange);
    verify(users).save(user);
    verify(emailVerification).startVerification(user);
  }

  @Test
  void requestEmailChangeDuplicatePrimaryReturns409() {
    UserModel user = sampleUser();
    when(userService.requireByToken("uid-1")).thenReturn(user);
    when(users.existsByEmail("taken@vanep.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.requestEmailChange(
                    "uid-1", new UserEmailChangeRequestDTO("taken@vanep.com")))
        .isInstanceOf(ProfileEmailDuplicateException.class)
        .satisfies(
            ex -> {
              ProfileEmailDuplicateException pe = (ProfileEmailDuplicateException) ex;
              assertThat(pe.getCode()).isEqualTo(ProfileErrorCode.EMAIL_DUPLICATE);
              assertThat(pe.getField()).isEqualTo("email");
              assertThat(pe.getRetryAfter()).isNull();
              assertThat(pe.getMessage()).isEqualTo("auth.signup.email.duplicate");
            });
    assertThat(user.getPendingEmail()).isNull();
    verify(emailVerification, never()).startVerification(any());
  }

  @Test
  void requestEmailChangeSameAsCurrentReturns400() {
    UserModel user = sampleUser();
    when(userService.requireByToken("uid-1")).thenReturn(user);

    assertThatThrownBy(
            () ->
                service.requestEmailChange(
                    "uid-1", new UserEmailChangeRequestDTO("test@vanep.com")))
        .isInstanceOf(ProfileBadRequestException.class)
        .satisfies(
            ex -> {
              ProfileBadRequestException error = (ProfileBadRequestException) ex;
              assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(error.getCode()).isEqualTo(ProfileErrorCode.EMAIL_SAME);
              assertThat(error.getField()).isEqualTo("email");
            });
    verify(users, never()).existsByEmail(any());
    verify(emailVerification, never()).startVerification(any());
  }

  @Test
  void requestEmailChangeCooldownReturns409() {
    UserModel user = sampleUser();
    Instant lastChange = Instant.now().minus(5, ChronoUnit.DAYS);
    user.setLastEmailChangeAt(lastChange);
    when(userService.requireByToken("uid-1")).thenReturn(user);
    when(users.existsByEmail("new@vanep.com")).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.requestEmailChange("uid-1", new UserEmailChangeRequestDTO("new@vanep.com")))
        .isInstanceOf(ProfileCooldownException.class)
        .satisfies(
            ex -> {
              ProfileCooldownException pce = (ProfileCooldownException) ex;
              assertThat(pce.getCode()).isEqualTo(ProfileErrorCode.COOLDOWN);
              assertThat(pce.getField()).isEqualTo("email");
              assertThat(pce.getRetryAfter()).isEqualTo(lastChange.plus(30, ChronoUnit.DAYS));
              assertThat(pce.getMessage()).isEqualTo("user.profile.email.cooldown");
            });
    assertThat(user.getPendingEmail()).isNull();
    verify(emailVerification, never()).startVerification(any());
  }

  @Test
  void requestEmailChangeReplacesPreviousPendingWithoutBlocking() {
    UserModel user = sampleUser();
    user.setPendingEmail("old-pending@vanep.com");
    when(userService.requireByToken("uid-1")).thenReturn(user);
    when(users.existsByEmail("newer@vanep.com")).thenReturn(false);

    service.requestEmailChange("uid-1", new UserEmailChangeRequestDTO("newer@vanep.com"));

    assertThat(user.getPendingEmail()).isEqualTo("newer@vanep.com");
    assertThat(user.getEmail()).isEqualTo("test@vanep.com");
    verify(emailVerification).startVerification(user);
  }

  private static UserModel sampleUser() {
    UserModel user = new UserModel();
    user.setToken("uid-1");
    user.setType(UserType.CLIENT);
    user.setName("Test User");
    user.setEmail("test@vanep.com");
    user.setDocument("12345678901");
    user.setPhone("11999999999");
    user.setBirthDate(LocalDate.of(1990, 5, 15));
    user.setGender(Gender.MALE);
    return user;
  }
}
