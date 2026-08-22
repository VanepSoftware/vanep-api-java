package br.com.vanep.client.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
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
class ClientControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private MockMvc mockMvc;

  private String clientToken;
  private String ownerUid;
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

    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Test Client");
    user.setEmail("client@vanep.com");
    user.setDocument("12345678901");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    ownerUid = user.getToken();

    ClientModel client = new ClientModel();
    client.setUser(user);
    client = clients.save(client);

    clientToken = client.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "admin-uid").claim("roles", List.of("ROLE_ADMIN")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_clients"),
            new SimpleGrantedAuthority("show_client"),
            new SimpleGrantedAuthority("delete_client"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", ownerUid).claim("roles", List.of("ROLE_CLIENT")))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private JwtRequestPostProcessor otherClientJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "other-uid").claim("roles", List.of("ROLE_CLIENT")))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private JwtRequestPostProcessor driverJwt(String driverUid) {
    return jwt()
        .jwt(t -> t.claim("uid", driverUid).claim("roles", List.of("ROLE_DRIVER")))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  private String addressBody(String street, String complement) {
    String complementJson = complement == null ? "null" : "\"" + complement + "\"";
    return "{\"cityToken\":\""
        + cityToken
        + "\",\"zipCode\":\"13015904\",\"street\":\""
        + street
        + "\",\"number\":\"1481\",\"complement\":"
        + complementJson
        + ",\"district\":\"Centro\"}";
  }

  private AddressModel persistLinkedHomeAddress() {
    CityModel city = cities.findByToken(cityToken).orElseThrow();
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet("Rua Barão de Jaguara");
    address.setNumber("1481");
    address.setComplement("Apto 12");
    address.setDistrict("Centro");
    address = addresses.save(address);

    ClientModel client = clients.findByToken(clientToken).orElseThrow();
    client.setAddressId(address.getId());
    clients.save(client);
    return address;
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsNonAdmin() throws Exception {
    mockMvc.perform(get("/api/clients").with(ownerJwt())).andExpect(status().isForbidden());
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/clients").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].token").value(clientToken))
        .andExpect(jsonPath("$.content[0].address").value(nullValue()))
        .andExpect(jsonPath("$.content[0].addressToken").doesNotExist());
  }

  @Test
  void listReturnsNestedAddressNotAddressToken() throws Exception {
    AddressModel address = persistLinkedHomeAddress();

    mockMvc
        .perform(get("/api/clients").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].address.token").value(address.getToken()))
        .andExpect(jsonPath("$.content[0].address.street").value("Rua Barão de Jaguara"))
        .andExpect(jsonPath("$.content[0].addressToken").doesNotExist());
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/clients/" + clientToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/clients/" + clientToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(clientToken))
        .andExpect(jsonPath("$.email").value("client@vanep.com"));
  }

  @Test
  void getByTokenReturnsNestedAddressNotAddressToken() throws Exception {
    AddressModel address = persistLinkedHomeAddress();

    mockMvc
        .perform(get("/api/clients/" + clientToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.token").value(address.getToken()))
        .andExpect(jsonPath("$.address.zipCode").value("13015904"))
        .andExpect(jsonPath("$.address.street").value("Rua Barão de Jaguara"))
        .andExpect(jsonPath("$.address.number").value("1481"))
        .andExpect(jsonPath("$.address.complement").value("Apto 12"))
        .andExpect(jsonPath("$.address.district").value("Centro"))
        .andExpect(jsonPath("$.address.cityToken").value(cityToken))
        .andExpect(jsonPath("$.address.cityName").value("Campinas"))
        .andExpect(jsonPath("$.address.stateUf").value("SP"))
        .andExpect(jsonPath("$.addressToken").doesNotExist());
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    mockMvc
        .perform(get("/api/clients/" + clientToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(clientToken))
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressToken").doesNotExist());
  }

  @Test
  void getByTokenReturns200ForCallerWithShowClientPermissionOnly() throws Exception {
    JwtRequestPostProcessor withShowClient =
        jwt()
            .jwt(t -> t.claim("uid", "other-uid"))
            .authorities(new SimpleGrantedAuthority("show_client"));

    mockMvc
        .perform(get("/api/clients/" + clientToken).with(withShowClient))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(clientToken));
  }

  @Test
  void getByTokenReturns403ForOtherClient() throws Exception {
    mockMvc
        .perform(get("/api/clients/" + clientToken).with(otherClientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/clients/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateReturns200ForOwnerWithoutAddressToken() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"photo\":\"https://example.com/photo.jpg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(clientToken))
        .andExpect(jsonPath("$.photo").value("https://example.com/photo.jpg"))
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressToken").doesNotExist());
  }

  @Test
  void updateIgnoresAddressTokenAndDoesNotLinkCatalog() throws Exception {
    CityModel city = cities.findByToken(cityToken).orElseThrow();
    AddressModel catalog = new AddressModel();
    catalog.setCity(city);
    catalog.setZipCode("01310100");
    catalog.setStreet("Avenida Paulista");
    catalog.setNumber("1000");
    catalog.setDistrict("Bela Vista");
    catalog = addresses.save(catalog);

    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"photo\":\"https://example.com/photo.jpg\",\"addressToken\":\""
                        + catalog.getToken()
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value("https://example.com/photo.jpg"))
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressToken").doesNotExist());

    mockMvc
        .perform(get("/api/clients/me").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value(nullValue()));
  }

  @Test
  void updateReturns403ForOtherClient() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .with(otherClientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns403ForAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns403ForNonExistentToken() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/doesnotexist")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/clients/" + clientToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteForbidsNonAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/clients/" + clientToken).with(ownerJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/clients/" + clientToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteClearsHomeAddress() throws Exception {
    AddressModel address = persistLinkedHomeAddress();
    String addressToken = address.getToken();

    mockMvc
        .perform(delete("/api/clients/" + clientToken).with(adminJwt()))
        .andExpect(status().isNoContent());

    assertThat(addresses.findByToken(addressToken)).isEmpty();
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(delete("/api/clients/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void meRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/clients/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meReturnsNullAddressWhenClientHasNone() throws Exception {
    mockMvc
        .perform(get("/api/clients/me").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(clientToken))
        .andExpect(jsonPath("$.user.email").value("client@vanep.com"))
        .andExpect(jsonPath("$.user.document").value("12345678901"))
        .andExpect(jsonPath("$.user.type").value("CLIENT"))
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressToken").doesNotExist());
  }

  @Test
  void meReturnsNestedAddressWhenLinked() throws Exception {
    AddressModel address = persistLinkedHomeAddress();

    mockMvc
        .perform(get("/api/clients/me").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.token").value(address.getToken()))
        .andExpect(jsonPath("$.address.zipCode").value("13015904"))
        .andExpect(jsonPath("$.address.street").value("Rua Barão de Jaguara"))
        .andExpect(jsonPath("$.address.number").value("1481"))
        .andExpect(jsonPath("$.address.complement").value("Apto 12"))
        .andExpect(jsonPath("$.address.district").value("Centro"))
        .andExpect(jsonPath("$.address.cityToken").value(cityToken))
        .andExpect(jsonPath("$.address.cityName").value("Campinas"))
        .andExpect(jsonPath("$.address.stateUf").value("SP"))
        .andExpect(jsonPath("$.addressToken").doesNotExist());
  }

  @Test
  void meReturns403ForDriverUid() throws Exception {
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Driver");
    driverUser.setEmail("driver@vanep.com");
    driverUser.setDocument("98765432100");
    driverUser.setVerified(true);
    final String driverUid = users.save(driverUser).getToken();

    mockMvc
        .perform(get("/api/clients/me").with(driverJwt(driverUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void putMeAddressRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/me/address")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressBody("Rua Barão de Jaguara", null)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void putMeAddressCreatesHomeAddress() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/me/address")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressBody("Rua Barão de Jaguara", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.street").value("Rua Barão de Jaguara"))
        .andExpect(jsonPath("$.cityToken").value(cityToken))
        .andExpect(jsonPath("$.cityName").value("Campinas"))
        .andExpect(jsonPath("$.stateUf").value("SP"));

    mockMvc
        .perform(get("/api/clients/me").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.street").value("Rua Barão de Jaguara"))
        .andExpect(jsonPath("$.address.cityToken").value(cityToken));
  }

  @Test
  void putMeAddressUpdatesHomeAddressInPlace() throws Exception {
    AddressModel address = persistLinkedHomeAddress();

    mockMvc
        .perform(
            put("/api/clients/me/address")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressBody("Rua Barão de Jaguara", "Fundos")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(address.getToken()))
        .andExpect(jsonPath("$.complement").value("Fundos"));
  }

  @Test
  void putMeAddressReturns403ForDriverUid() throws Exception {
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Driver");
    driverUser.setEmail("driver-put@vanep.com");
    driverUser.setDocument("11122233344");
    driverUser.setVerified(true);
    final String driverUid = users.save(driverUser).getToken();

    mockMvc
        .perform(
            put("/api/clients/me/address")
                .with(driverJwt(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressBody("Rua Barão de Jaguara", null)))
        .andExpect(status().isForbidden());
  }

  @Test
  void putMeAddressReturns400ForInvalidZipCode() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/me/address")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cityToken\":\""
                        + cityToken
                        + "\",\"zipCode\":\"13\",\"street\":\"Rua\",\"number\":\"1\",\"district\":\"Centro\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteMeAddressRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/clients/me/address")).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteMeAddressReturns204AndClears() throws Exception {
    persistLinkedHomeAddress();

    mockMvc
        .perform(delete("/api/clients/me/address").with(ownerJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/clients/me").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value(nullValue()));
  }

  @Test
  void deleteMeAddressWhenNoneReturns204() throws Exception {
    mockMvc
        .perform(delete("/api/clients/me/address").with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteMeAddressReturns403ForDriverUid() throws Exception {
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Driver");
    driverUser.setEmail("driver-del@vanep.com");
    driverUser.setDocument("55566677788");
    driverUser.setVerified(true);
    final String driverUid = users.save(driverUser).getToken();

    mockMvc
        .perform(delete("/api/clients/me/address").with(driverJwt(driverUid)))
        .andExpect(status().isForbidden());
  }
}
