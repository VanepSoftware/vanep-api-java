package br.com.vanep.address.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class AddressControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;

  private MockMvc mockMvc;
  private String addressToken;
  private String cityToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state = states.save(state);

    CityModel city = new CityModel();
    city.setState(state);
    city.setName("Campinas");
    city = cities.save(city);
    cityToken = city.getToken();

    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet("Rua Barão de Jaguara");
    address.setNumber("1481");
    address.setDistrict("Centro");
    address = addresses.save(address);
    addressToken = address.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "admin-uid")
                    .claim("roles", List.of("ROLE_ADMIN"))
                    .subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_addresses"),
            new SimpleGrantedAuthority("show_address"),
            new SimpleGrantedAuthority("create_address"),
            new SimpleGrantedAuthority("update_address"),
            new SimpleGrantedAuthority("delete_address"));
  }

  private JwtRequestPostProcessor noPermissionJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "client-uid")
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject("client@vanep.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private String bodyWith(String cityTokenValue, String street) {
    return "{\"cityToken\":\""
        + cityTokenValue
        + "\",\"zipCode\":\"13015904\",\"street\":\""
        + street
        + "\",\"number\":\"100\",\"district\":\"Centro\"}";
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsUserWithoutPermission() throws Exception {
    mockMvc
        .perform(get("/api/addresses").with(noPermissionJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/addresses").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].token").value(addressToken))
        .andExpect(jsonPath("$.content[0].stateUf").value("SP"));
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/addresses/" + addressToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/addresses/" + addressToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(addressToken))
        .andExpect(jsonPath("$.street").value("Rua Barão de Jaguara"))
        .andExpect(jsonPath("$.cityName").value("Campinas"));
  }

  @Test
  void getByTokenReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(get("/api/addresses/" + addressToken).with(noPermissionJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/addresses/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsUserWithoutPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(noPermissionJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith(cityToken, "Rua Nova")))
        .andExpect(status().isForbidden());
  }

  @Test
  void createReturns201ForAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith(cityToken, "Rua Nova")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.street").value("Rua Nova"))
        .andExpect(jsonPath("$.cityToken").value(cityToken));
  }

  @Test
  void createReturns400WhenStreetBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith(cityToken, "")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns400WhenZipCodeInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cityToken\":\""
                        + cityToken
                        + "\",\"zipCode\":\"123\",\"street\":\"Rua X\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns400WhenCityTokenBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zipCode\":\"13015904\",\"street\":\"Rua X\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns404WhenCityMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith("doesnotexist", "Rua Nova")))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/addresses/" + addressToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith(cityToken, "Avenida Atualizada")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.street").value("Avenida Atualizada"));
  }

  @Test
  void updateReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(
            put("/api/addresses/" + addressToken)
                .with(noPermissionJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith(cityToken, "Rua X")))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(
            put("/api/addresses/doesnotexist")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith(cityToken, "Rua X")))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/addresses/" + addressToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/addresses/" + addressToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(delete("/api/addresses/" + addressToken).with(noPermissionJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(delete("/api/addresses/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200AfterDelete() throws Exception {
    mockMvc.perform(delete("/api/addresses/" + addressToken).with(adminJwt()));

    mockMvc
        .perform(post("/api/addresses/" + addressToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(addressToken));
  }

  @Test
  void restoreReturns409WhenNotDeleted() throws Exception {
    mockMvc
        .perform(post("/api/addresses/" + addressToken + "/restore").with(adminJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void restoreReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(post("/api/addresses/doesnotexist/restore").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(post("/api/addresses/" + addressToken + "/restore").with(noPermissionJwt()))
        .andExpect(status().isForbidden());
  }
}
