package br.com.vanep.auth.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityErrorContractTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void unauthenticatedApiRequestReturnsProblemDetailWithWwwAuthenticateHeader() throws Exception {
    mockMvc
        .perform(get("/api/conversations/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("auth.unauthenticated"))
        .andExpect(
            jsonPath("$.detail").value("Autenticação necessária. Entre novamente para continuar."))
        .andExpect(jsonPath("$.instance").value("/api/conversations/me"));
  }

  @Test
  void invalidBearerTokenReturnsProblemDetailWithErrorHeader() throws Exception {
    mockMvc
        .perform(
            get("/api/conversations/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
  }

  @Test
  void preAuthorizeDenialReturnsProblemDetailAuthForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/conversations/me")
                .with(jwt().jwt(token -> token.claim("uid", "user-1").subject("user-1"))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("auth.forbidden"))
        .andExpect(jsonPath("$.detail").value("Você não tem permissão para realizar esta ação."));
  }

  @Test
  void unknownApiRouteReturnsRouteNotFoundProblemDetail() throws Exception {
    mockMvc
        .perform(
            get("/api/non-existent-route")
                .with(jwt().jwt(token -> token.claim("uid", "user-1").subject("user-1"))))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("request.route_not_found"))
        .andExpect(jsonPath("$.detail").value("Rota não encontrada."));
  }

  @Test
  void malformedJsonReturnsBodyUnreadableProblemDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/clients/me/service-requests")
                .with(jwt().jwt(token -> token.claim("uid", "user-1").subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{bad"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("request.body_unreadable"));
  }
}
