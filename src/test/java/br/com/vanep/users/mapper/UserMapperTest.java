package br.com.vanep.users.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.users.dto.UserResponseDto;
import br.com.vanep.users.entity.UserEntity;
import br.com.vanep.users.enums.UserTypeEnum;
import org.junit.jupiter.api.Test;

class UserMapperTest {

  private final UserMapper mapper = new UserMapper();

  @Test
  void toResponse_mapsAllFields() {
    UserEntity entity =
        UserEntity.builder()
            .id(1)
            .token("tok")
            .type(UserTypeEnum.CLIENT)
            .name("N")
            .email("e@e.com")
            .username("u")
            .cpf("123")
            .verified(true)
            .phone("999")
            .password("x")
            .build();

    UserResponseDto dto = mapper.toResponse(entity);

    assertThat(dto.token()).isEqualTo("tok");
    assertThat(dto.type()).isEqualTo(UserTypeEnum.CLIENT);
    assertThat(dto.name()).isEqualTo("N");
    assertThat(dto.email()).isEqualTo("e@e.com");
    assertThat(dto.username()).isEqualTo("u");
    assertThat(dto.cpf()).isEqualTo("123");
    assertThat(dto.verified()).isTrue();
    assertThat(dto.phone()).isEqualTo("999");
  }
}
