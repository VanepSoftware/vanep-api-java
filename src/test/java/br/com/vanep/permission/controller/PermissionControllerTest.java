package br.com.vanep.permission.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class PermissionControllerTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "admin-uid"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_role_permissions"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "client-uid"))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/permissions")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsWithoutPermission() throws Exception {
    mockMvc.perform(get("/api/permissions").with(clientJwt())).andExpect(status().isForbidden());
  }

  @Test
  void listReturnsRegistryForAuthorized() throws Exception {
    mockMvc
        .perform(get("/api/permissions").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasItem("list_drivers")))
        .andExpect(jsonPath("$", hasItem("create_role_permission")));
  }
}
