package br.com.vanep.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.notification.enums.NotificationType;
import br.com.vanep.notification.model.NotificationModel;
import br.com.vanep.notification.repository.NotificationRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class NotificationControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private NotificationRepository notifications;
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
    mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
  }

  @Test
  void listsOnlyTheCallersNotificationsNewestFirst() throws Exception {
    notifications.save(notification(maria, NotificationType.PROPOSAL_RECEIVED, "2026-11-01"));
    notifications.save(notification(maria, NotificationType.CONTRACT_CREATED, "2026-11-02"));
    notifications.save(notification(carlos, NotificationType.PROPOSAL_ACCEPTED, "2026-11-03"));

    mockMvc
        .perform(get("/api/notifications").with(userJwt(maria)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].type").value("CONTRACT_CREATED"))
        .andExpect(jsonPath("$.content[0].title").value("title"))
        .andExpect(jsonPath("$.content[0].data.resourceToken").value("r-1"))
        .andExpect(jsonPath("$.content[0].readAt").doesNotExist())
        .andExpect(jsonPath("$.content[1].type").value("PROPOSAL_RECEIVED"));
  }

  @Test
  void pagesTheList() throws Exception {
    for (int day = 1; day <= 3; day++) {
      notifications.save(notification(maria, NotificationType.CHAT_MESSAGE, "2026-11-0" + day));
    }

    mockMvc
        .perform(
            get("/api/notifications").param("page", "1").param("size", "2").with(userJwt(maria)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void countsAndMarksReadOnlyTheCallersNotifications() throws Exception {
    NotificationModel first =
        notifications.save(notification(maria, NotificationType.CHAT_MESSAGE, "2026-11-01"));
    notifications.save(notification(maria, NotificationType.CHAT_MESSAGE, "2026-11-02"));
    notifications.save(notification(carlos, NotificationType.CHAT_MESSAGE, "2026-11-03"));

    mockMvc
        .perform(get("/api/notifications/unread-count").with(userJwt(maria)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(2));

    mockMvc
        .perform(post("/api/notifications/" + first.getToken() + "/read").with(userJwt(maria)))
        .andExpect(status().isNoContent());
    Instant readAt = notifications.findById(first.getId()).orElseThrow().getReadAt();
    assertThat(readAt).isNotNull();

    mockMvc
        .perform(post("/api/notifications/" + first.getToken() + "/read").with(userJwt(maria)))
        .andExpect(status().isNoContent());
    assertThat(notifications.findById(first.getId()).orElseThrow().getReadAt()).isEqualTo(readAt);

    mockMvc
        .perform(post("/api/notifications/read-all").with(userJwt(maria)))
        .andExpect(status().isNoContent());
    assertThat(notifications.countByUserIdAndReadAtIsNull(maria.getId())).isZero();
    assertThat(notifications.countByUserIdAndReadAtIsNull(carlos.getId())).isOne();
  }

  @Test
  void noOneMarksAnotherUsersNotificationRead() throws Exception {
    NotificationModel marias =
        notifications.save(notification(maria, NotificationType.CHAT_MESSAGE, "2026-11-01"));

    mockMvc
        .perform(post("/api/notifications/" + marias.getToken() + "/read").with(userJwt(carlos)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/notifications/unknown/read").with(userJwt(carlos)))
        .andExpect(status().isForbidden());

    assertThat(notifications.findById(marias.getId()).orElseThrow().getReadAt()).isNull();
  }

  private NotificationModel notification(UserModel owner, NotificationType type, String day) {
    NotificationModel notification = new NotificationModel();
    notification.setUserId(owner.getId());
    notification.setType(type);
    notification.setTitle("title");
    notification.setBody("body");
    notification.setData(Map.of("resourceToken", "r-1"));
    notification.setCreatedAt(Instant.parse(day + "T12:00:00Z"));
    return notification;
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
