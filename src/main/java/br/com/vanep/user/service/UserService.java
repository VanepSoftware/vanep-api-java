package br.com.vanep.user.service;

import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.model.UserModel;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

  private final UserRepository users;
  private final MessageSource messages;

  public UserService(UserRepository users, MessageSource messages) {
    this.users = users;
    this.messages = messages;
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
    return new UserMeResponseDTO(
        user.getToken(),
        user.getName(),
        user.getPhone(),
        user.getEmail(),
        user.getDocument(),
        user.getBirthDate(),
        user.getGender(),
        user.getType().name());
  }
}
