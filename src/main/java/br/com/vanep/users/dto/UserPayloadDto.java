package br.com.vanep.users.dto;

import br.com.vanep.users.enums.UserTypeEnum;

public record UserPayloadDto(
    String name,
    UserTypeEnum type,
    String email,
    String username,
    String password,
    String cpf,
    String phone) {}
