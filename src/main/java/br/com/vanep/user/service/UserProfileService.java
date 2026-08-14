package br.com.vanep.user.service;

import br.com.vanep.user.Gender;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.dto.UserProfileUpdateRequestDTO;
import br.com.vanep.user.exception.ProfileBadRequestException;
import br.com.vanep.user.exception.ProfileCooldownException;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.policy.UserProfileChangePolicy;
import java.time.Instant;
import java.util.Objects;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

  private final UserService userService;
  private final UserRepository users;
  private final UserProfileChangePolicy policy;
  private final MessageSource messages;

  public UserProfileService(
      UserService userService,
      UserRepository users,
      UserProfileChangePolicy policy,
      MessageSource messages) {
    this.userService = userService;
    this.users = users;
    this.policy = policy;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
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

  boolean applyName(UserModel user, JsonNullable<String> nameField, Instant now) {
    if (!nameField.isPresent()) {
      return false;
    }
    String name = nameField.get();
    rejectIfNull(name, "name");
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
      throw ProfileBadRequestException.phoneBlank(message("user.profile.phone.blank"));
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
    rejectIfNull(gender, "gender");
    if (Objects.equals(gender, user.getGender())) {
      return false;
    }
    user.setGender(gender);
    return true;
  }

  void rejectIfNull(Object value, String field) {
    if (value == null) {
      throw ProfileBadRequestException.fieldNull(message("user.profile.field.null"), field);
    }
  }

  void assertCooldown(Instant lastChangeAt, Instant now, String field, String messageKey) {
    policy
        .retryAfter(lastChangeAt, now)
        .ifPresent(
            retryAfter -> {
              throw new ProfileCooldownException(message(messageKey), field, retryAfter);
            });
  }
}
