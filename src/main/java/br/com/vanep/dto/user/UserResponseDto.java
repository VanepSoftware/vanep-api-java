package br.com.vanep.dto.user;

import br.com.vanep.enums.UserTypeEnum;

public record UserResponseDto(
    String token,
    UserTypeEnum type,
    String name,
    String email,
    String username,
    String cpf,
    Boolean verified,
    String phone) {}
