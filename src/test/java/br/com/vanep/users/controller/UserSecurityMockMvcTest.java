package br.com.vanep.users.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.users.dto.UserRequestDto;
import br.com.vanep.users.dto.UserResponseDto;
import br.com.vanep.users.enums.UserTypeEnum;
import br.com.vanep.users.service.UserService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class UserSecurityMockMvcTest {

  @Autowired private WebApplicationContext webApplicationContext;

  @MockitoBean private UserService userService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void getUsers_withoutBasic_returns401() throws Exception {
    mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void getUsers_withBasic_returns200() throws Exception {
    when(userService.findAll()).thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/users").header(HttpHeaders.AUTHORIZATION, basicAuth("api-user", "changeme")))
        .andExpect(status().isOk());
  }

  @Test
  void postUsers_withoutBasic_returns201() throws Exception {
    when(userService.create(any(UserRequestDto.class)))
        .thenReturn(
            new UserResponseDto("t", UserTypeEnum.CLIENT, "N", "a@b.com", "u", "1", false, null));

    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "N",
                      "type": "CLIENT",
                      "email": "a@b.com",
                      "username": "u",
                      "password": "secret",
                      "cpf": "123",
                      "phone": null
                    }
                    """))
        .andExpect(status().isCreated());
  }

  private static String basicAuth(String user, String password) {
    String raw = user + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
