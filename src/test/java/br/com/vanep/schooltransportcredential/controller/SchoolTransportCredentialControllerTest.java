package br.com.vanep.schooltransportcredential.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.schooltransportcredential.SchoolTransportCredentialFixtures;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SchoolTransportCredentialControllerTest {

  @Autowired private WebApplicationContext webContext;
  @Autowired private ApplicationContext context;
  @Autowired private VehicleRepository vehicles;

  private MockMvc mockMvc;
  private SchoolTransportCredentialFixtures fixtures;
  private String ownerUid;
  private String strangerUid;
  private VehicleModel van;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    fixtures = new SchoolTransportCredentialFixtures(context);
    DriverModel owner = fixtures.createDriver("dono@vanep.com", "12345678909");
    ownerUid = owner.getUser().getToken();
    strangerUid = fixtures.createDriver("estranho@vanep.com", "15350946056").getUser().getToken();
    van = fixtures.createVehicle(owner, "ABC1D23");
    fixtures.approveTheDriversDocuments(owner, "01234567890");
  }

  private String path() {
    return "/api/vehicles/" + van.getToken() + "/school-transport-credentials";
  }

  @Test
  void aNewVanWithoutDocumentsShowsTheThreeVehicleRequirementsMissing() throws Exception {
    mockMvc
        .perform(get(path()).with(callerWithUid(ownerUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vehicleToken").value(van.getToken()))
        .andExpect(jsonPath("$.date").exists())
        .andExpect(jsonPath("$.satisfied").value(false))
        .andExpect(jsonPath("$.requirements.length()").value(7))
        .andExpect(jsonPath("$.requirements[0].requirement").value("DRIVER_LICENSE"))
        .andExpect(jsonPath("$.requirements[0].satisfied").value(true))
        .andExpect(jsonPath("$.requirements[0].number").value("01234567890"))
        .andExpect(jsonPath("$.requirements[1].satisfied").value(true))
        .andExpect(jsonPath("$.requirements[2].satisfied").value(true))
        .andExpect(jsonPath("$.requirements[3].requirement").value("VEHICLE_REGISTRATION"))
        .andExpect(jsonPath("$.requirements[3].satisfied").value(false))
        .andExpect(jsonPath("$.requirements[4].satisfied").value(false))
        .andExpect(jsonPath("$.requirements[5].satisfied").value(false))
        .andExpect(jsonPath("$.requirements[6].requirement").value("MUNICIPAL_AUTHORIZATION"))
        .andExpect(jsonPath("$.requirements[6].optional").value(true))
        .andExpect(jsonPath("$.requirements[6].satisfied").value(false));
  }

  @Test
  void aCompleteVanIsSatisfiedWithItsNumbers() throws Exception {
    fixtures.approveTheVansDocuments(van);

    mockMvc
        .perform(get(path()).with(callerWithUid(ownerUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.satisfied").value(true))
        .andExpect(jsonPath("$.requirements[5].number").value("AUT-118"))
        .andExpect(
            jsonPath("$.requirements[5].validUntil")
                .value(SchoolTransportCredentialFixtures.VALID_UNTIL.toString()));
  }

  @Test
  void anAdminWithShowVehicleReadsIt() throws Exception {
    mockMvc
        .perform(
            get(path())
                .with(
                    jwt()
                        .jwt(token -> token.claim("uid", "admin-uid"))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("show_vehicle"))))
        .andExpect(status().isOk());
  }

  @Test
  void anotherDriverIsRefused() throws Exception {
    mockMvc.perform(get(path()).with(callerWithUid(strangerUid))).andExpect(status().isForbidden());
  }

  @Test
  void withoutTokenIsUnauthorized() throws Exception {
    mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
  }

  @Test
  void anInactiveVanIsUnprocessable() throws Exception {
    fixtures.approveTheVansDocuments(van);
    van.setActive(false);
    vehicles.save(van);

    mockMvc
        .perform(get(path()).with(callerWithUid(ownerUid)))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void aRemovedVanIsNotFound() throws Exception {
    vehicles.delete(van);

    mockMvc.perform(get(path()).with(callerWithUid(ownerUid))).andExpect(status().isNotFound());
  }

  private static JwtRequestPostProcessor callerWithUid(String uid) {
    return jwt()
        .jwt(token -> token.claim("uid", uid))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }
}
