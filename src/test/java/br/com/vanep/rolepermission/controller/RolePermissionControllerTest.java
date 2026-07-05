package br.com.vanep.rolepermission.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.rolepermission.model.RolePermissionModel;
import br.com.vanep.rolepermission.repository.RolePermissionRepository;
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
class RolePermissionControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private RolePermissionRepository bundles;

  private MockMvc mockMvc;
  private String bundleToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    RolePermissionModel bundle = new RolePermissionModel();
    bundle.setName("Test Bundle");
    bundle.setPermissions(List.of("list_roles", "show_role"));
    bundle = bundles.save(bundle);
    bundleToken = bundle.getToken();
  }

  private JwtRequestPostProcessor withAuthority(String authority) {
    return jwt()
        .jwt(t -> t.claim("uid", "admin-uid"))
        .authorities(new SimpleGrantedAuthority(authority));
  }

  private JwtRequestPostProcessor noAuthority() {
    return jwt().jwt(t -> t.claim("uid", "client-uid"));
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/role-permissions")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsWithoutPermission() throws Exception {
    mockMvc
        .perform(get("/api/role-permissions").with(noAuthority()))
        .andExpect(status().isForbidden());
  }

  @Test
  void listReturns200WithPermission() throws Exception {
    mockMvc
        .perform(get("/api/role-permissions").with(withAuthority("list_role_permissions")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].token").value(bundleToken));
  }

  @Test
  void getReturns200WithPermission() throws Exception {
    mockMvc
        .perform(
            get("/api/role-permissions/" + bundleToken).with(withAuthority("show_role_permission")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(bundleToken));
  }

  @Test
  void getForbidsWithoutPermission() throws Exception {
    mockMvc
        .perform(get("/api/role-permissions/" + bundleToken).with(noAuthority()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(
            get("/api/role-permissions/doesnotexist").with(withAuthority("show_role_permission")))
        .andExpect(status().isNotFound());
  }

  @Test
  void createReturns201WithPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/role-permissions")
                .with(withAuthority("create_role_permission"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Bundle\",\"permissions\":[\"list_roles\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("New Bundle"))
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void createForbidsWithoutPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/role-permissions")
                .with(noAuthority())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Bundle\",\"permissions\":[\"list_roles\"]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void createReturns400WhenPermissionUnknown() throws Exception {
    mockMvc
        .perform(
            post("/api/role-permissions")
                .with(withAuthority("create_role_permission"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Bundle\",\"permissions\":[\"fly_to_moon\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns400WhenNameBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/role-permissions")
                .with(withAuthority("create_role_permission"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"permissions\":[\"list_roles\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturns200WithPermission() throws Exception {
    mockMvc
        .perform(
            put("/api/role-permissions/" + bundleToken)
                .with(withAuthority("update_role_permission"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Bundle\",\"permissions\":[\"delete_role\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Bundle"));
  }

  @Test
  void updateForbidsWithoutPermission() throws Exception {
    mockMvc
        .perform(
            put("/api/role-permissions/" + bundleToken)
                .with(noAuthority())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"permissions\":[\"list_roles\"]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(
            put("/api/role-permissions/doesnotexist")
                .with(withAuthority("update_role_permission"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"permissions\":[\"list_roles\"]}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteReturns204WithPermission() throws Exception {
    mockMvc
        .perform(
            delete("/api/role-permissions/" + bundleToken)
                .with(withAuthority("delete_role_permission")))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteForbidsWithoutPermission() throws Exception {
    mockMvc
        .perform(delete("/api/role-permissions/" + bundleToken).with(noAuthority()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(
            delete("/api/role-permissions/doesnotexist")
                .with(withAuthority("delete_role_permission")))
        .andExpect(status().isNotFound());
  }
}
