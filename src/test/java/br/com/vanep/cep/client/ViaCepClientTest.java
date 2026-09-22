package br.com.vanep.cep.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.vanep.cep.dto.ViaCepResponseDTO;
import br.com.vanep.cep.exception.ViaCepLookupException;
import br.com.vanep.cep.exception.ViaCepNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ViaCepClientTest {
  private static final String BASE_URL = "https://viacep.test.invalid";
  private static final String BRASILIA_CEP = "70040010";
  private static final String UNKNOWN_CEP = "99999999";

  private MockRestServiceServer server;
  private ViaCepClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new ViaCepClient(builder.build());
  }

  private String fixture(String name) throws IOException {
    return new ClassPathResource("fixtures/viacep/" + name + ".json")
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private String lookupUrl(String cep) {
    return BASE_URL + "/ws/" + cep + "/json/";
  }

  @Test
  void mapsIbgeCodeFromARecordedBrasiliaFixture() throws IOException {
    server
        .expect(ExpectedCount.once(), requestTo(lookupUrl(BRASILIA_CEP)))
        .andRespond(withSuccess(fixture("df-brasilia-asa-norte"), MediaType.APPLICATION_JSON));

    ViaCepResponseDTO response = client.findByCep(BRASILIA_CEP);

    assertThat(response.ibge()).isEqualTo("5300108");
    assertThat(response.cityName()).isEqualTo("Brasília");
    assertThat(response.uf()).isEqualTo("DF");
    assertThat(response.street()).isEqualTo("Quadra SBN Quadra 1");
    assertThat(response.neighborhood()).isEqualTo("Asa Norte");
    server.verify();
  }

  @Test
  void treatsErroTrueAsNotFound() throws IOException {
    server
        .expect(ExpectedCount.once(), requestTo(lookupUrl(UNKNOWN_CEP)))
        .andRespond(withSuccess(fixture("unknown-cep"), MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.findByCep(UNKNOWN_CEP))
        .isInstanceOf(ViaCepNotFoundException.class);
    server.verify();
  }

  @Test
  void treatsBooleanErroTrueAsNotFound() {
    server
        .expect(ExpectedCount.once(), requestTo(lookupUrl(UNKNOWN_CEP)))
        .andRespond(withSuccess("{ \"erro\": true }", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.findByCep(UNKNOWN_CEP))
        .isInstanceOf(ViaCepNotFoundException.class);
    server.verify();
  }

  @Test
  void translatesConnectionFailureIntoLookupException() {
    server
        .expect(ExpectedCount.once(), requestTo(lookupUrl(BRASILIA_CEP)))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> client.findByCep(BRASILIA_CEP))
        .isInstanceOf(ViaCepLookupException.class);
    server.verify();
  }
}
