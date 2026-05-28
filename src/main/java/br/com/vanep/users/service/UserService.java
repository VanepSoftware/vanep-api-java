package br.com.vanep.users.service;

import br.com.vanep.config.PasswordHasher;
import br.com.vanep.users.dto.UserRequestDto;
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
  public UserResponseDto create(UserRequestDto request) {
    UserEntity user = new UserEntity();
    applyRequest(user, request, true);
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
  public UserResponseDto update(String token, UserRequestDto request) {
    UserEntity user = getByToken(token);
    applyRequest(user, request, false);
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

  private void applyRequest(UserEntity user, UserRequestDto request, boolean isCreate) {
    user.setType(request.type());
    user.setName(request.name());
    user.setEmail(request.email());
    user.setUsername(request.username());
    user.setCpf(request.cpf());
    user.setPhone(request.phone());

    if (isCreate) {
      user.setVerified(false);
      user.setPassword(passwordHasher.encode(request.password()));
      return;
    }

    if (request.password() != null && !request.password().isBlank()) {
      user.setPassword(passwordHasher.encode(request.password()));
    }
  }
}
