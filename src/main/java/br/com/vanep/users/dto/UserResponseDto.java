package br.com.vanep.users.dto;

import br.com.vanep.users.enums.UserTypeEnum;

public record UserResponseDto(
    String token,
    UserTypeEnum type,
    String name,
    String email,
    String username,
    String cpf,
    Boolean verified,
    String phone) {}
