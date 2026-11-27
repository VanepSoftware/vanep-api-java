package br.com.vanep.user.service;

import br.com.vanep.auth.verification.EmailVerificationService;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.UserProfileFieldLimits;
import br.com.vanep.user.dto.UserEmailChangeRequestDTO;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.dto.UserProfileUpdateRequestDTO;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.policy.UserProfileChangePolicy;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.util.Objects;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

  private final UserService userService;
  private final UserRepository users;
  private final UserProfileChangePolicy policy;
  private final EmailVerificationService emailVerification;

  public UserProfileService(
      UserService userService,
      UserRepository users,
      UserProfileChangePolicy policy,
      EmailVerificationService emailVerification) {
    this.userService = userService;
    this.users = users;
    this.policy = policy;
    this.emailVerification = emailVerification;
  }

  public UserMeResponseDTO patchMe(String uid, UserProfileUpdateRequestDTO request) {
    UserModel user = userService.requireByToken(uid);
    Instant now = Instant.now();
    boolean dirty = false;

    dirty |= applyName(user, request.name(), now);
    dirty |= applyPhone(user, request.phone(), now);
    dirty |= applyGender(user, request.gender());

    if (dirty) {
      users.save(user);
    }
    return userService.toMeResponse(user);
  }

  @Transactional
  public void requestEmailChange(String uid, UserEmailChangeRequestDTO request) {
    UserModel user = userService.requireByToken(uid);
    String newEmail = request.email();

    if (Objects.equals(newEmail, user.getEmail())) {
      throw ApiProblemException.badRequest("user.profile.email.same").with("field", "email");
    }
    if (users.existsByEmail(newEmail)) {
      throw ApiProblemException.conflict("auth.signup.email.duplicate").with("field", "email");
    }

    Instant now = Instant.now();
    assertCooldown(user.getLastEmailChangeAt(), now, "email", "user.profile.email.cooldown");
    assertConfirmationSendAllowed(user);

    user.setPendingEmail(newEmail);
    users.save(user);
    emailVerification.startVerification(user);
  }

  boolean applyName(UserModel user, JsonNullable<String> nameField, Instant now) {
    if (!nameField.isPresent()) {
      return false;
    }
    String name = nameField.get();
    rejectIfNull(name, "name");
    if (name.length() > UserProfileFieldLimits.NAME_MAX) {
      throw ApiProblemException.badRequest(
              "user.profile.name.too_long", UserProfileFieldLimits.NAME_MAX)
          .with("field", "name");
    }
    if (Objects.equals(name, user.getName())) {
      return false;
    }
    assertCooldown(user.getLastNameChangeAt(), now, "name", "user.profile.name.cooldown");
    user.setName(name);
    user.setLastNameChangeAt(now);
    return true;
  }

  boolean applyPhone(UserModel user, JsonNullable<String> phoneField, Instant now) {
    if (!phoneField.isPresent()) {
      return false;
    }
    String phone = phoneField.get();
    rejectIfNull(phone, "phone");
    if (phone.isBlank()) {
      throw ApiProblemException.badRequest("user.profile.phone.blank").with("field", "phone");
    }
    if (phone.length() > UserProfileFieldLimits.PHONE_MAX) {
      throw ApiProblemException.badRequest(
              "user.profile.phone.too_long", UserProfileFieldLimits.PHONE_MAX)
          .with("field", "phone");
    }
    if (Objects.equals(phone, user.getPhone())) {
      return false;
    }
    assertCooldown(user.getLastPhoneChangeAt(), now, "phone", "user.profile.phone.cooldown");
    user.setPhone(phone);
    user.setLastPhoneChangeAt(now);
    return true;
  }

  boolean applyGender(UserModel user, JsonNullable<Gender> genderField) {
    if (!genderField.isPresent()) {
      return false;
    }
    Gender gender = genderField.get();
    if (Objects.equals(gender, user.getGender())) {
      return false;
    }
    user.setGender(gender);
    return true;
  }

  void rejectIfNull(Object value, String field) {
    if (value == null) {
      throw ApiProblemException.badRequest("user.profile.field.null").with("field", field);
    }
  }

  /**
   * {@code lastEmailChangeAt} only moves when a change is confirmed, so without this the same
   * pending e-mail could be requested — and mailed — in a loop.
   */
  void assertConfirmationSendAllowed(UserModel user) {
    emailVerification
        .issueRetryAfter(user)
        .ifPresent(
            retryAfter -> {
              throw ApiProblemException.conflict("user.profile.email.resend.cooldown")
                  .with("field", "email")
                  .with("retryAfter", retryAfter);
            });
  }

  void assertCooldown(Instant lastChangeAt, Instant now, String field, String messageKey) {
    policy
        .retryAfter(lastChangeAt, now)
        .ifPresent(
            retryAfter -> {
              throw ApiProblemException.conflict(messageKey)
                  .with("field", field)
                  .with("retryAfter", retryAfter);
            });
  }
}
