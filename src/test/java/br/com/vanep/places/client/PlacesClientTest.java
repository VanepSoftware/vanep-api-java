package br.com.vanep.places.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.places.exception.PlaceLookupException;
import br.com.vanep.places.exception.PlaceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PlacesClientTest {
  private static final String BASE_URL = "https://places.test.invalid";
  private static final String PLACE_ID = "ChIJiQLoU9TMW5MRbx2OMMN5r-o";
  private static final String DETAILS_URL = BASE_URL + "/v1/places/" + PLACE_ID;

  private MockRestServiceServer server;
  private PlacesClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new PlacesClient(builder.build(), 60, 100);
  }

  private String fixture(String name) throws IOException {
    return new ClassPathResource("fixtures/places/" + name + ".json")
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private void expectSuccessOnce(String fixtureName) throws IOException {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andExpect(header("X-Goog-FieldMask", PlacesClient.FIELD_MASK))
        .andRespond(withSuccess(fixture(fixtureName), MediaType.APPLICATION_JSON));
  }

  @Test
  void parsesAddressComponentsFromARealFixture() throws IOException {
    expectSuccessOnce("df-taguatinga-qnl5");

    PlaceDetailsResponseDTO details = client.findPlaceDetails(PLACE_ID);

    String idInFixture =
        new ObjectMapper().readTree(fixture("df-taguatinga-qnl5")).get("id").asText();
    assertThat(details.id()).isEqualTo(idInFixture);
    assertThat(details.addressComponents())
        .extracting(component -> component.longText())
        .contains("Taguatinga", "Brasília", "Distrito Federal", "Brazil");
    server.verify();
  }

  @Test
  void keepsComponentWithoutTypesInsteadOfFailing() throws IOException {
    expectSuccessOnce("df-escola-objetivo");

    PlaceDetailsResponseDTO details = client.findPlaceDetails(PLACE_ID);

    assertThat(details.addressComponents()).anyMatch(component -> component.hasNoTypes());
    assertThat(details.addressComponents())
        .filteredOn(component -> component.hasNoTypes())
        .allSatisfy(component -> assertThat(component.types()).isNotNull().isEmpty());
    server.verify();
  }

  @Test
  void requestsOnlyTheEssentialsFieldMask() throws IOException {
    expectSuccessOnce("df-aguas-claras");

    client.findPlaceDetails(PLACE_ID);

    assertThat(PlacesClient.FIELD_MASK).isEqualTo("id,formattedAddress,addressComponents,types");
    server.verify();
  }

  @Test
  void closesTheAutocompleteSessionWithTheFreeIdOnlyMask() throws IOException {
    server
        .expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(DETAILS_URL)))
        .andExpect(queryParam("sessionToken", "session-abc"))
        .andExpect(header("X-Goog-FieldMask", "id"))
        .andRespond(withSuccess(fixture("df-taguatinga-qnl5"), MediaType.APPLICATION_JSON));

    client.closeAutocompleteSession(PLACE_ID, "session-abc");

    server.verify();
  }

  @Test
  void doesNotCallGoogleToCloseASessionThatDoesNotExist() {
    client.closeAutocompleteSession(PLACE_ID, null);
    client.closeAutocompleteSession(PLACE_ID, "  ");

    server.verify();
  }

  @Test
  void forwardsSessionTokenToGoogle() throws IOException {
    server
        .expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(DETAILS_URL)))
        .andExpect(queryParam("sessionToken", "session-abc"))
        .andRespond(withSuccess(fixture("df-taguatinga-qnl5"), MediaType.APPLICATION_JSON));

    client.findPlaceDetails(PLACE_ID, "session-abc");

    server.verify();
  }

  @Test
  void omitsSessionTokenParameterWhenAbsent() throws IOException {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withSuccess(fixture("df-taguatinga-qnl5"), MediaType.APPLICATION_JSON));

    client.findPlaceDetails(PLACE_ID, null);

    server.verify();
  }

  @Test
  void servesFromCacheWhenThereIsNoSessionToken() throws IOException {
    expectSuccessOnce("df-taguatinga-qnl5");

    PlaceDetailsResponseDTO first = client.findPlaceDetails(PLACE_ID);
    PlaceDetailsResponseDTO second = client.findPlaceDetails(PLACE_ID);

    assertThat(second).isSameAs(first);
    server.verify();
  }

  @Test
  void alwaysCallsGoogleWhenSessionTokenIsPresentEvenOnCacheHit() throws IOException {
    server
        .expect(ExpectedCount.twice(), requestTo(org.hamcrest.Matchers.startsWith(DETAILS_URL)))
        .andRespond(withSuccess(fixture("df-taguatinga-qnl5"), MediaType.APPLICATION_JSON));

    client.findPlaceDetails(PLACE_ID);
    client.findPlaceDetails(PLACE_ID, "session-abc");

    server.verify();
  }

  @Test
  void refreshesCacheWithTheSessionResponse() throws IOException {
    server
        .expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(DETAILS_URL)))
        .andRespond(withSuccess(fixture("df-taguatinga-qnl5"), MediaType.APPLICATION_JSON));

    PlaceDetailsResponseDTO fromSession = client.findPlaceDetails(PLACE_ID, "session-abc");
    PlaceDetailsResponseDTO fromCache = client.findPlaceDetails(PLACE_ID);

    assertThat(fromCache).isSameAs(fromSession);
    server.verify();
  }

  @Test
  void translatesNotFoundIntoPlaceNotFound() {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> client.findPlaceDetails(PLACE_ID))
        .isInstanceOf(PlaceNotFoundException.class);
  }

  @Test
  void translatesBadRequestIntoPlaceNotFound() {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.findPlaceDetails(PLACE_ID))
        .isInstanceOf(PlaceNotFoundException.class);
  }

  @Test
  void translatesRejectedCredentialIntoLookupFailureNotNotFound() {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> client.findPlaceDetails(PLACE_ID))
        .isInstanceOf(PlaceLookupException.class);
  }

  @Test
  void translatesQuotaExhaustedIntoLookupFailure() {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> client.findPlaceDetails(PLACE_ID))
        .isInstanceOf(PlaceLookupException.class);
  }

  @Test
  void translatesServerErrorIntoLookupFailure() {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> client.findPlaceDetails(PLACE_ID))
        .isInstanceOf(PlaceLookupException.class);
  }

  @Test
  void translatesTimeoutIntoLookupFailure() {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withException(new java.net.SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> client.findPlaceDetails(PLACE_ID))
        .isInstanceOf(PlaceLookupException.class);
  }

  @Test
  void doesNotCacheAFailedLookup() {
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    server
        .expect(ExpectedCount.once(), requestTo(DETAILS_URL))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> client.findPlaceDetails(PLACE_ID))
        .isInstanceOf(PlaceLookupException.class);
    assertThatThrownBy(() -> client.findPlaceDetails(PLACE_ID))
        .isInstanceOf(PlaceLookupException.class);

    server.verify();
  }

  @Test
  void rejectsBlankPlaceId() {
    assertThatThrownBy(() -> client.findPlaceDetails("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
