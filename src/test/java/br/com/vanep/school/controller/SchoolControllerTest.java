package br.com.vanep.school.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
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
class SchoolControllerTest {
  @Autowired private WebApplicationContext context;
  @Autowired private SchoolRepository schools;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private MockMvc mockMvc;
  private String schoolToken;
  private String cityToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country.setLocale("pt-BR");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    state = states.save(state);

    CityModel city = new CityModel();
    city.setState(state);
    city.setName("Campinas");
    city = cities.save(city);
    cityToken = city.getToken();

    SchoolModel school = new SchoolModel();
    school.setName("Escola Teste");
    school = schools.save(school);
    schoolToken = school.getToken();
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
            new SimpleGrantedAuthority("list_schools"),
            new SimpleGrantedAuthority("show_school"),
            new SimpleGrantedAuthority("create_school"),
            new SimpleGrantedAuthority("update_school"),
            new SimpleGrantedAuthority("delete_school"),
            new SimpleGrantedAuthority("restore_school"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "client-uid")
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject("client@vanep.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private AddressModel persistSchoolAddress(SchoolModel school, String street, String number) {
    CityModel city = cities.findByToken(cityToken).orElseThrow();
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet(street);
    address.setNumber(number);
    address = addresses.save(address);
    school.setAddressId(address.getId());
    schools.save(school);
    return address;
  }

  private String addressJson(String street, String number) {
    return "{\"cityToken\":\""
        + cityToken
        + "\",\"zipCode\":\"13015904\",\"street\":\""
        + street
        + "\",\"number\":\""
        + number
        + "\",\"district\":\"Centro\"}";
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/schools")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsUserWithoutPermission() throws Exception {
    mockMvc.perform(get("/api/schools").with(clientJwt())).andExpect(status().isForbidden());
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/schools").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].token").value(schoolToken))
        .andExpect(jsonPath("$.content[0].addressId").doesNotExist());
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/schools/" + schoolToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/schools/" + schoolToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(schoolToken))
        .andExpect(jsonPath("$.name").value("Escola Teste"))
        .andExpect(jsonPath("$.cnpj").doesNotExist())
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressId").doesNotExist());
  }

  @Test
  void getByTokenReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(get("/api/schools/" + schoolToken).with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/schools/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/schools").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsUserWithoutPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/schools")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nova Escola\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void createReturns201ForAdmin() throws Exception {
    String body =
        """
        {
          "name": "Nova Escola"
        }
        """;
    mockMvc
        .perform(
            post("/api/schools")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Nova Escola"))
        .andExpect(jsonPath("$.cnpj").doesNotExist())
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressId").doesNotExist());
  }

  @Test
  void createWithNestedAddressReturns201AndOmitsAddressId() throws Exception {
    mockMvc
        .perform(
            post("/api/schools")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Escola Com Endereco\",\"address\":"
                        + addressJson("Rua da Escola", "100")
                        + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.address.street").value("Rua da Escola"))
        .andExpect(jsonPath("$.address.number").value("100"))
        .andExpect(jsonPath("$.address.cityToken").value(cityToken))
        .andExpect(jsonPath("$.address.token").isNotEmpty())
        .andExpect(jsonPath("$.addressId").doesNotExist());
  }

  @Test
  void createIgnoresNumericAddressIdAndDoesNotLinkIt() throws Exception {
    AddressModel catalog =
        persistSchoolAddress(schools.findByToken(schoolToken).orElseThrow(), "Avenida", "1");

    mockMvc
        .perform(
            post("/api/schools")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Escola Sem Catalogo\",\"addressId\":" + catalog.getId() + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressId").doesNotExist());

    SchoolModel created =
        schools.findAll().stream()
            .filter(school -> "Escola Sem Catalogo".equals(school.getName()))
            .findFirst()
            .orElseThrow();
    assertThat(created.getAddressId()).isNull();
  }

  @Test
  void createReturns400WhenNameBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/schools")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(
            patch("/api/schools/" + schoolToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Escola Atualizada\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Escola Atualizada"))
        .andExpect(jsonPath("$.cnpj").doesNotExist())
        .andExpect(jsonPath("$.addressId").doesNotExist());
  }

  @Test
  void updateReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(
            patch("/api/schools/" + schoolToken)
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(
            patch("/api/schools/doesnotexist")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void putSchoolIsNotMapped() throws Exception {
    mockMvc
        .perform(
            put("/api/schools/" + schoolToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Escola Atualizada\"}"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
  }

  @Test
  void patchNestedAddressUpdatesOwnedRowInPlace() throws Exception {
    SchoolModel school = schools.findByToken(schoolToken).orElseThrow();
    AddressModel owned = persistSchoolAddress(school, "Rua da Escola", "10");

    mockMvc
        .perform(
            patch("/api/schools/" + schoolToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":" + addressJson("Rua da Escola", "99") + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.token").value(owned.getToken()))
        .andExpect(jsonPath("$.address.number").value("99"))
        .andExpect(jsonPath("$.addressId").doesNotExist());
  }

  @Test
  void patchNullAddressClearsOwnedAddress() throws Exception {
    SchoolModel school = schools.findByToken(schoolToken).orElseThrow();
    AddressModel owned = persistSchoolAddress(school, "Rua da Escola", "10");
    String ownedToken = owned.getToken();

    mockMvc
        .perform(
            patch("/api/schools/" + schoolToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressId").doesNotExist());

    SchoolModel reloaded = schools.findByToken(schoolToken).orElseThrow();
    assertThat(reloaded.getAddressId()).isNull();
    assertThat(addresses.findByToken(ownedToken)).isEmpty();
  }

  @Test
  void patchPresentBlankNameReturns400AndLeavesStoredName() throws Exception {
    mockMvc
        .perform(
            patch("/api/schools/" + schoolToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());

    assertThat(schools.findByToken(schoolToken).orElseThrow().getName()).isEqualTo("Escola Teste");
  }

  @Test
  void patchPresentNullNameReturns400AndLeavesStoredName() throws Exception {
    mockMvc
        .perform(
            patch("/api/schools/" + schoolToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":null}"))
        .andExpect(status().isBadRequest());

    assertThat(schools.findByToken(schoolToken).orElseThrow().getName()).isEqualTo("Escola Teste");
  }

  @Test
  void deleteRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/schools/" + schoolToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/schools/" + schoolToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(delete("/api/schools/" + schoolToken).with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(delete("/api/schools/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteClearsOwnedAddress() throws Exception {
    SchoolModel school = schools.findByToken(schoolToken).orElseThrow();
    AddressModel owned = persistSchoolAddress(school, "Rua da Escola", "10");
    String ownedToken = owned.getToken();

    mockMvc
        .perform(delete("/api/schools/" + schoolToken).with(adminJwt()))
        .andExpect(status().isNoContent());

    assertThat(addresses.findByToken(ownedToken)).isEmpty();
  }

  @Test
  void restoreReturns200AfterDelete() throws Exception {
    mockMvc.perform(delete("/api/schools/" + schoolToken).with(adminJwt()));

    mockMvc
        .perform(post("/api/schools/" + schoolToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(schoolToken));
  }

  @Test
  void restoreSchoolHasAddressNull() throws Exception {
    SchoolModel school = schools.findByToken(schoolToken).orElseThrow();
    persistSchoolAddress(school, "Rua da Escola", "10");

    mockMvc.perform(delete("/api/schools/" + schoolToken).with(adminJwt()));

    mockMvc
        .perform(post("/api/schools/" + schoolToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressId").doesNotExist());
  }

  @Test
  void restoreReturns409WhenNotDeleted() throws Exception {
    mockMvc
        .perform(post("/api/schools/" + schoolToken + "/restore").with(adminJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void restoreReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(post("/api/schools/doesnotexist/restore").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(post("/api/schools/" + schoolToken + "/restore").with(clientJwt()))
        .andExpect(status().isForbidden());
  }
}
