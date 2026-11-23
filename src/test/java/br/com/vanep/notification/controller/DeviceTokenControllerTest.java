package br.com.vanep.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.notification.enums.DevicePlatform;
import br.com.vanep.notification.model.DeviceTokenModel;
import br.com.vanep.notification.repository.DeviceTokenRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DeviceTokenControllerTest {

  private static final String FCM_TOKEN = "dGVzdC1mY20tdG9rZW4:APA91b-test_token";

  @Autowired private WebApplicationContext context;
  @Autowired private DeviceTokenRepository devices;
  @Autowired private UserRepository users;

  private MockMvc mockMvc;
  private UserModel maria;
  private UserModel carlos;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    maria = createUser(UserType.CLIENT, "Maria", "maria@vanep.com", "11144477735");
    carlos = createUser(UserType.DRIVER, "Carlos", "carlos@vanep.com", "52998224725");
  }

  @Test
  void rejectsUnauthenticatedCalls() throws Exception {
    mockMvc.perform(register(FCM_TOKEN, "ANDROID")).andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/devices/" + FCM_TOKEN)).andExpect(status().isUnauthorized());
    assertThat(devices.count()).isZero();
  }

  @Test
  void registersTheCallersDevice() throws Exception {
    mockMvc
        .perform(register(FCM_TOKEN, "IOS").with(userJwt(maria)))
        .andExpect(status().isNoContent());

    DeviceTokenModel device = devices.findByFcmToken(FCM_TOKEN).orElseThrow();
    assertThat(device.getUserId()).isEqualTo(maria.getId());
    assertThat(device.getPlatform()).isEqualTo(DevicePlatform.IOS);
    assertThat(device.getLastSeenAt()).isNotNull();
  }

  @Test
  void registeringTheSameDeviceTwiceKeepsOneRow() throws Exception {
    mockMvc.perform(register(FCM_TOKEN, "ANDROID").with(userJwt(maria)));
    mockMvc
        .perform(register(FCM_TOKEN, "ANDROID").with(userJwt(maria)))
        .andExpect(status().isNoContent());

    assertThat(devices.count()).isOne();
  }

  @Test
  void aDeviceSignedInByAnotherUserMovesToThem() throws Exception {
    mockMvc.perform(register(FCM_TOKEN, "ANDROID").with(userJwt(maria)));
    mockMvc
        .perform(register(FCM_TOKEN, "ANDROID").with(userJwt(carlos)))
        .andExpect(status().isNoContent());

    assertThat(devices.findFcmTokensByUserId(maria.getId())).isEmpty();
    assertThat(devices.findFcmTokensByUserId(carlos.getId())).containsExactly(FCM_TOKEN);
  }

  @Test
  void rejectsAnInvalidBody() throws Exception {
    mockMvc
        .perform(register("   ", "ANDROID").with(userJwt(maria)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(register(FCM_TOKEN, "SYMBIAN").with(userJwt(maria)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            register("x".repeat(DeviceTokenModel.FCM_TOKEN_MAX_LENGTH + 1), "IOS")
                .with(userJwt(maria)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/devices")
                .with(userJwt(maria))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fcmToken\":\"" + FCM_TOKEN + "\"}"))
        .andExpect(status().isBadRequest());
    assertThat(devices.count()).isZero();
  }

  @Test
  void acceptsATokenExactlyAtTheLengthLimit() throws Exception {
    mockMvc
        .perform(
            register("x".repeat(DeviceTokenModel.FCM_TOKEN_MAX_LENGTH), "WEB").with(userJwt(maria)))
        .andExpect(status().isNoContent());
  }

  @Test
  void unregistersTheCallersDevice() throws Exception {
    mockMvc.perform(register(FCM_TOKEN, "ANDROID").with(userJwt(maria)));

    mockMvc
        .perform(delete("/api/devices/" + FCM_TOKEN).with(userJwt(maria)))
        .andExpect(status().isNoContent());

    assertThat(devices.findByFcmToken(FCM_TOKEN)).isEmpty();
  }

  @Test
  void cannotUnregisterAnotherUsersDevice() throws Exception {
    mockMvc.perform(register(FCM_TOKEN, "ANDROID").with(userJwt(maria)));

    mockMvc
        .perform(delete("/api/devices/" + FCM_TOKEN).with(userJwt(carlos)))
        .andExpect(status().isNoContent());

    assertThat(devices.findByFcmToken(FCM_TOKEN)).isPresent();
  }

  @Test
  void aTokenWithoutAUserIsRejected() throws Exception {
    mockMvc
        .perform(register(FCM_TOKEN, "ANDROID").with(jwt()))
        .andExpect(status().isUnauthorized());
    assertThat(devices.count()).isZero();
  }

  private MockHttpServletRequestBuilder register(String fcmToken, String platform) {
    return post("/api/devices")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"fcmToken\":\"" + fcmToken + "\",\"platform\":\"" + platform + "\"}");
  }

  private JwtRequestPostProcessor userJwt(UserModel user) {
    return jwt().jwt(token -> token.claim("uid", user.getToken()).subject(user.getToken()));
  }

  private UserModel createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }
}
