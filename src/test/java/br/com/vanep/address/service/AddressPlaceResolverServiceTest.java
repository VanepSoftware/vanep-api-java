package br.com.vanep.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.AddressComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AddressPlaceResolverServiceTest {
  @Mock private PlacesClient places;
  @Mock private LocationResolverService resolver;

  private AddressPlaceResolverService service;
  private CityModel city;
  private DistrictModel district;

  @BeforeEach
  void setUp() {
    StaticMessageSource messages = new StaticMessageSource();
    messages.setUseCodeAsDefaultMessage(true);
    service = new AddressPlaceResolverService(places, resolver, messages);

    city = new CityModel();
    city.setName("Brasilia");

    district = new DistrictModel();
    district.setName("Taguatinga");
  }

  @Test
  void fillsEveryResolvedColumn() {
    givenResolvedPlace(streetPlaceWithNumber("1481"));
    AddressModel address = new AddressModel();

    service.applyPlace(address, "place-1", "session-1", null, "Apartamento 22");

    assertThat(address.getCity()).isSameAs(city);
    assertThat(address.getDistrict()).isSameAs(district);
    assertThat(address.getStreet()).isEqualTo("Rua Barao de Jaguara");
    assertThat(address.getZipCode()).isEqualTo("13015904");
    assertThat(address.getGooglePlaceId()).isEqualTo("place-1");
    assertThat(address.getComplement()).isEqualTo("Apartamento 22");
  }

  @Test
  void prefersTheNumberSentByTheCaller() {
    givenResolvedPlace(streetPlaceWithNumber("1481"));
    AddressModel address = new AddressModel();

    service.applyPlace(address, "place-1", null, "340", null);

    assertThat(address.getNumber()).isEqualTo("340");
  }

  @Test
  void fallsBackToTheNumberFromThePlace() {
    givenResolvedPlace(streetPlaceWithNumber("1481"));
    AddressModel address = new AddressModel();

    service.applyPlace(address, "place-1", null, "  ", null);

    assertThat(address.getNumber()).isEqualTo("1481");
  }

  @Test
  void leavesTheNumberNullWhenNeitherSideHasOne() {
    givenResolvedPlace(streetPlaceWithNumber(null));
    AddressModel address = new AddressModel();

    service.applyPlace(address, "place-1", null, null, null);

    assertThat(address.getNumber()).isNull();
  }

  @Test
  void rejectsAPlaceWithoutAStreet() {
    given(places.findPlaceDetails("place-1", null)).willReturn(cityWidePlace());
    AddressModel address = new AddressModel();

    assertThatThrownBy(() -> service.applyPlace(address, "place-1", null, null, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("location.address.street_required");

    assertThat(address.getStreet()).isNull();
  }

  @Test
  void acceptsAPlaceWithoutADistrict() {
    given(places.findPlaceDetails("place-1", null)).willReturn(streetPlaceWithNumber("10"));
    given(resolver.resolveAndPersist(any()))
        .willReturn(new ResolvedLocationChainDTO(null, null, city, List.of(), false));
    AddressModel address = new AddressModel();

    service.applyPlace(address, "place-1", null, null, null);

    assertThat(address.getDistrict()).isNull();
    assertThat(address.getCity()).isSameAs(city);
  }

  private void givenResolvedPlace(PlaceDetailsResponseDTO details) {
    given(places.findPlaceDetails(any(), any())).willReturn(details);
    given(resolver.resolveAndPersist(any()))
        .willReturn(new ResolvedLocationChainDTO(null, null, city, List.of(district), true));
  }

  private PlaceDetailsResponseDTO streetPlaceWithNumber(String number) {
    List<AddressComponentDTO> components =
        new java.util.ArrayList<>(
            List.of(
                component("Rua Barao de Jaguara", "route"),
                component("13015904", "postal_code"),
                component("Taguatinga", "administrative_area_level_4")));
    if (number != null) {
      components.add(component(number, "street_number"));
    }
    return new PlaceDetailsResponseDTO("place-1", "Rua Barao de Jaguara", components);
  }

  private PlaceDetailsResponseDTO cityWidePlace() {
    return new PlaceDetailsResponseDTO(
        "place-1", "Brasilia - DF", List.of(component("Brasilia", "administrative_area_level_2")));
  }

  private AddressComponentDTO component(String text, String type) {
    return new AddressComponentDTO(text, text, List.of(type, "political"));
  }
}
