package br.com.vanep.users.service;

import br.com.vanep.config.PasswordHasher;
import br.com.vanep.users.dto.UserPayloadDto;
import br.com.vanep.users.dto.UserResponseDto;
import br.com.vanep.users.entity.UserEntity;
import br.com.vanep.users.mapper.UserMapper;
import br.com.vanep.users.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final UserMapper userMapper;

  public UserService(
      UserRepository userRepository, PasswordHasher passwordHasher, UserMapper userMapper) {
    this.userRepository = userRepository;
    this.passwordHasher = passwordHasher;
    this.userMapper = userMapper;
  }

  @Transactional
  public UserResponseDto create(UserPayloadDto payload) {
    UserEntity user = new UserEntity();
    applyPayload(user, payload, true);
    return userMapper.toResponse(userRepository.save(user));
  }

  @Transactional(readOnly = true)
  public List<UserResponseDto> findAll() {
    return userRepository.findAll().stream().map(userMapper::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public UserResponseDto findByToken(String token) {
    return userMapper.toResponse(getByToken(token));
  }

  @Transactional
  public UserResponseDto update(String token, UserPayloadDto payload) {
    UserEntity user = getByToken(token);
    applyPayload(user, payload, false);
    return userMapper.toResponse(userRepository.save(user));
  }

  @Transactional
  public void delete(String token) {
    userRepository.delete(getByToken(token));
  }

  private UserEntity getByToken(String token) {
    return userRepository
        .findByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  private void applyPayload(UserEntity user, UserPayloadDto payload, boolean isCreate) {
    user.setType(payload.type());
    user.setName(payload.name());
    user.setEmail(payload.email());
    user.setUsername(payload.username());
    user.setCpf(payload.cpf());
    user.setPhone(payload.phone());

    if (isCreate) {
      user.setVerified(false);
      user.setPassword(passwordHasher.encode(payload.password()));
      return;
    }

    if (payload.password() != null && !payload.password().isBlank()) {
      user.setPassword(passwordHasher.encode(payload.password()));
    }
  }
}
