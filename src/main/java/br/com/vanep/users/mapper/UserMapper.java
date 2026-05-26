package br.com.vanep.users.mapper;

import br.com.vanep.users.dto.UserResponseDto;
import br.com.vanep.users.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public UserResponseDto toResponse(UserEntity user) {
    return new UserResponseDto(
        user.getToken(),
        user.getType(),
        user.getName(),
        user.getEmail(),
        user.getUsername(),
        user.getCpf(),
        user.getVerified(),
        user.getPhone());
  }
}
