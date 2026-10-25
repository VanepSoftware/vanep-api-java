package br.com.vanep.clientdriver.controller;

import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ClientDriverBlockControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ClientDriverRepository links;
  @Autowired private ContractRepository contracts;
  @Autowired private MessageSource messages;
  @Autowired private JdbcTemplate jdbc;

  private MockMvc mockMvc;
  private ServiceRequestControllerFixtures fixtures;
  private ClientModel ana;
  private UserModel anaUser;
  private DriverModel carlos;
  private UserModel carlosUser;
  private DependentModel lucas;
  private String requestToken;
  private ClientDriverModel link;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    fixtures = new ServiceRequestControllerFixtures(context);
    ana = fixtures.createReadyClient("Ana Souza", "ana@vanep.com", "11144477735");
    anaUser = ana.getUser();
    carlos = fixtures.createAvailableDriver("Carlos Lima", "carlos@vanep.com", "52998224725");
    carlosUser = carlos.getUser();
    lucas = fixtures.createDependentGoingEveryWeekday(ana, "Lucas Souza", LocalDate.of(2016, 5, 4));
    requestToken = fixtures.sendRequest(ana, carlos, lucas);
    link = links.findByPair(ana.getId(), carlos.getId()).orElseThrow();
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(post(block())).andExpect(status().isUnauthorized());
    mockMvc.perform(post(unblock())).andExpect(status().isUnauthorized());
  }

  @Test
  void onlyThePartiesOfTheLinkReachTheRoutes() throws Exception {
    UserModel bruno =
        fixtures.createReadyClient("Bruno", "bruno@vanep.com", "12345678909").getUser();
    UserModel admin = fixtures.createAdmin("admin@vanep.com", "39053344705");

    for (String path : List.of(block(), unblock())) {
      mockMvc.perform(post(path).with(as(bruno))).andExpect(status().isForbidden());
      mockMvc.perform(post(path).with(as(admin))).andExpect(status().isForbidden());
    }
    mockMvc
        .perform(post("/api/client-drivers/unknown/block").with(as(anaUser)))
        .andExpect(status().isForbidden());
    assertThat(linkRow()).containsEntry("status", "PENDING");
  }

  @Test
  void theClientBlocksAndTheOpenRequestIsWithdrawn() throws Exception {
    mockMvc
        .perform(post(block()).with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.blockedBy").value("CLIENT"));

    assertThat(linkRow())
        .containsEntry("status", "BLOCKED")
        .containsEntry("blocked_by", anaUser.getId());
    assertThat(linkRow().get("blocked_at")).isNotNull();
    assertThat(requestStatus()).isEqualTo("WITHDRAWN");
  }

  @Test
  void theDriverBlocksTheOpenRequestIsDeclinedAndTheClientCannotAskAgain() throws Exception {
    mockMvc
        .perform(post(block()).with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blockedBy").value("DRIVER"));
    assertThat(requestStatus()).isEqualTo("DECLINED");

    assertThatThrownBy(() -> fixtures.sendRequest(ana, carlos, lucas))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(e.getReason()).isEqualTo(message("service_request.link.blocked"));
            });
  }

  @Test
  void blockingTwiceIsAConflict() throws Exception {
    mockMvc.perform(post(block()).with(as(carlosUser))).andExpect(status().isOk());

    mockMvc
        .perform(post(block()).with(as(anaUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("client_driver.already_blocked")));
    assertThat(linkRow()).containsEntry("blocked_by", carlosUser.getId());
  }

  @Test
  void aContractInProgressPreventsTheBlock() throws Exception {
    saveContract(ContractStatus.ACTIVE);

    mockMvc
        .perform(post(block()).with(as(anaUser)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(message("client_driver.block.has_contract")));
    assertThat(linkRow()).containsEntry("status", "PENDING");
    assertThat(requestStatus()).isEqualTo("OPEN");
  }

  @Test
  void theBlockerUnblocksToPending() throws Exception {
    mockMvc.perform(post(block()).with(as(carlosUser))).andExpect(status().isOk());

    mockMvc
        .perform(post(unblock()).with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.blockedBy").doesNotExist());
    assertThat(linkRow()).containsEntry("blocked_by", null).containsEntry("blocked_at", null);
  }

  @Test
  void theBlockerUnblocksToInactiveAfterAnEndedContract() throws Exception {
    saveContract(ContractStatus.ENDED);
    mockMvc.perform(post(block()).with(as(anaUser))).andExpect(status().isOk());

    mockMvc
        .perform(post(unblock()).with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
  }

  @Test
  void theBlockedPartyCannotUnblock() throws Exception {
    mockMvc.perform(post(block()).with(as(carlosUser))).andExpect(status().isOk());

    mockMvc
        .perform(post(unblock()).with(as(anaUser)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value(message("client_driver.unblock.not_blocker")));
    assertThat(linkRow()).containsEntry("status", "BLOCKED");
  }

  @Test
  void unblockingALinkThatIsNotBlockedIsAConflict() throws Exception {
    mockMvc
        .perform(post(unblock()).with(as(anaUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("client_driver.not_blocked")));
  }

  private String block() {
    return "/api/client-drivers/" + link.getToken() + "/block";
  }

  private String unblock() {
    return "/api/client-drivers/" + link.getToken() + "/unblock";
  }

  private Map<String, Object> linkRow() {
    return jdbc.queryForMap(
        "select status, blocked_by, blocked_at from client_driver where id = ?", link.getId());
  }

  private String requestStatus() {
    return jdbc.queryForObject(
        "select status from service_request where token = ?", String.class, requestToken);
  }

  private void saveContract(ContractStatus status) {
    ContractModel contract = new ContractModel();
    contract.setClientDriver(link);
    contract.setStatus(status);
    contract.setStartsOn(LocalDate.of(2027, 2, 1));
    contract.setEndsOn(LocalDate.of(2027, 12, 1));
    contract.setTotalAmount(new BigDecimal("4200.00"));
    contract.setInstallments(12);
    contract.setDueDay(5);
    contracts.save(contract);
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
