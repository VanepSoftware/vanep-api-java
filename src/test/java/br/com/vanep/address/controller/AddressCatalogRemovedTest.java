package br.com.vanep.address.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class AddressCatalogRemovedTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
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

  @Test
  void getAddressesIsNotMappedEvenWithAdminJwt() throws Exception {
    mockMvc.perform(get("/api/addresses").with(adminJwt())).andExpect(status().isNotFound());
  }

  @Test
  void postAddressesIsNotMappedEvenWithAdminJwt() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cityToken\":\"city-tok\",\"zipCode\":\"13015904\",\"street\":\"Rua Nova\",\"number\":\"100\",\"district\":\"Centro\"}"))
        .andExpect(status().isNotFound());
  }
}
