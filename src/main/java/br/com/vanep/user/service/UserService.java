package br.com.vanep.user.service;

import br.com.vanep.auth.verification.EmailVerificationTokenRepository;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.policy.UserProfileChangePolicy;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
  private final UserRepository users;
  private final MessageSource messages;
  private final EmailVerificationTokenRepository verificationTokens;
  private final UserProfileChangePolicy profileChangePolicy;
  private final OnboardingService onboardingService;

  public UserService(
      UserRepository users,
      MessageSource messages,
      EmailVerificationTokenRepository verificationTokens,
      UserProfileChangePolicy profileChangePolicy,
      OnboardingService onboardingService) {
    this.users = users;
    this.messages = messages;
    this.verificationTokens = verificationTokens;
    this.profileChangePolicy = profileChangePolicy;
    this.onboardingService = onboardingService;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  public UserMeResponseDTO getMe(String uid) {
    return toMeResponse(requireByToken(uid));
  }

  public UserModel requireByToken(String uid) {
    return users
        .findByToken(uid)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.account.not_found")));
  }

  public UserModel requireByTokenAndType(String uid, UserType expected) {
    UserModel user = requireByToken(uid);
    if (user.getType() != expected) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("user.type.forbidden"));
    }
    return user;
  }

  public UserMeResponseDTO toMeResponse(UserModel user) {
    Instant now = Instant.now();
    return new UserMeResponseDTO(
        user.getToken(),
        user.getName(),
        user.getPhone(),
        user.getEmail(),
        user.getDocument(),
        user.getBirthDate(),
        user.getGender(),
        user.getType().name(),
        resolvePendingEmailForMe(user, now).orElse(null),
        profileChangePolicy.retryAfter(user.getLastNameChangeAt(), now).orElse(null),
        profileChangePolicy.retryAfter(user.getLastPhoneChangeAt(), now).orElse(null),
        profileChangePolicy.retryAfter(user.getLastEmailChangeAt(), now).orElse(null),
        onboardingService.findPendingSteps(user));
  }

  Optional<String> resolvePendingEmailForMe(UserModel user, Instant now) {
    String pending = user.getPendingEmail();
    if (pending == null || pending.isBlank()) {
      return Optional.empty();
    }
    if (user.getId() == null) {
      return Optional.empty();
    }
    boolean hasOpenToken =
        verificationTokens.existsByUserIdAndConsumedAtIsNullAndExpiresAtAfter(user.getId(), now);
    return hasOpenToken ? Optional.of(pending) : Optional.empty();
  }
}
