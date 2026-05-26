package br.com.vanep.users.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.users.dto.UserPayloadDto;
import br.com.vanep.users.dto.UserResponseDto;
import br.com.vanep.users.enums.UserTypeEnum;
import br.com.vanep.users.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "vanep.security.permit-all=true")
class UserControllerMockMvcTest {

  @Autowired private WebApplicationContext webApplicationContext;

  @MockitoBean private UserService userService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void postUsers_returns201AndBody() throws Exception {
    UserResponseDto response =
        new UserResponseDto("abc", UserTypeEnum.CLIENT, "N", "a@b.com", "u", "123", false, null);
    when(userService.create(any(UserPayloadDto.class))).thenReturn(response);

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
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").value("abc"));

    verify(userService).create(any(UserPayloadDto.class));
  }

  @Test
  void getUsers_returns200() throws Exception {
    when(userService.findAll())
        .thenReturn(
            List.of(
                new UserResponseDto(
                    "t", UserTypeEnum.DRIVER, "X", "x@x.com", "ux", "999", true, "1")));

    mockMvc
        .perform(get("/api/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].token").value("t"));
  }

  @Test
  void getUserByToken_returns200() throws Exception {
    when(userService.findByToken("tok"))
        .thenReturn(
            new UserResponseDto(
                "tok", UserTypeEnum.ADMIN, "A", "a@a.com", "adm", "111", true, null));

    mockMvc
        .perform(get("/api/users/tok"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("adm"));
  }

  @Test
  void putUser_returns200() throws Exception {
    UserResponseDto response =
        new UserResponseDto("tok", UserTypeEnum.CLIENT, "N2", "b@b.com", "u2", "222", false, "9");
    when(userService.update(eq("tok"), any(UserPayloadDto.class))).thenReturn(response);

    mockMvc
        .perform(
            put("/api/users/tok")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(samplePayload())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("N2"));
  }

  @Test
  void deleteUser_returns204() throws Exception {
    mockMvc.perform(delete("/api/users/tok")).andExpect(status().isNoContent());

    verify(userService).delete("tok");
  }

  private static UserPayloadDto samplePayload() {
    return new UserPayloadDto("N2", UserTypeEnum.CLIENT, "b@b.com", "u2", null, "22222222222", "9");
  }
}
