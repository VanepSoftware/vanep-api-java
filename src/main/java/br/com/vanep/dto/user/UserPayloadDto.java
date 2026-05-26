package br.com.vanep.dto.user;

import br.com.vanep.enums.UserTypeEnum;

public record UserPayloadDto( 
    String name,
    UserTypeEnum type,
    String email,
    String username,
    String password,
    String cpf,
    String phone) {}
