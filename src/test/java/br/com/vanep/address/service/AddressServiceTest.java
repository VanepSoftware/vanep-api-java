package br.com.vanep.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.mapper.AddressMapper;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.state.model.StateModel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

  @Mock private AddressRepository addressRepository;
  @Mock private CityRepository cityRepository;
  @Mock private AddressMapper mapper;
  @Mock private MessageSource messages;

  private AddressService service;

  @BeforeEach
  void setUp() {
    service = new AddressService(addressRepository, cityRepository, mapper, messages);
  }

  private CityModel city() {
    CountryModel country = new CountryModel();
    country.setId(100L);
    country.setToken("country-br");
    country.setName("Brasil");

    StateModel state = new StateModel();
    state.setId(1L);
    state.setToken("state-sp");
    state.setUf("SP");
    state.setCountry(country);

    CityModel city = new CityModel();
    city.setId(10L);
    city.setToken("city-campinas");
    city.setName("Campinas");
    city.setState(state);
    return city;
  }

  private AddressModel addressWithToken(String token) {
    AddressModel address = new AddressModel();
    address.setToken(token);
    address.setCity(city());
    address.setZipCode("13015904");
    address.setStreet("Rua Barão de Jaguara");
    address.setNumber("1481");
    return address;
  }

  private AddressResponseDTO responseFor(String token) {
    return new AddressResponseDTO(
        token,
        "13015904",
        "Rua Barão de Jaguara",
        "1481",
        null,
        "Centro",
        "city-campinas",
        "Campinas",
        "SP",
        true,
        null);
  }

  private AddressRequestDTO requestFor(String cityToken, String street) {
    return new AddressRequestDTO(cityToken, "13015904", street, "1481", null, "Centro");
  }

  @Test
  void findAllReturnsPagedResponses() {
    AddressModel address = addressWithToken("abc");
    AddressResponseDTO response = responseFor("abc");
    when(addressRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(address)));
    when(mapper.toResponse(address)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    AddressModel address = addressWithToken("tok");
    AddressResponseDTO response = responseFor("tok");
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));
    when(mapper.toResponse(address)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(addressRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void createPersistsAddress() {
    AddressModel saved = addressWithToken("tok");
    AddressResponseDTO response = responseFor("tok");
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(any(AddressModel.class))).thenReturn(saved);
    when(mapper.toResponse(saved)).thenReturn(response);

    AddressResponseDTO result = service.create(requestFor("city-campinas", "Rua Barão de Jaguara"));

    assertThat(result).isEqualTo(response);
    verify(addressRepository).save(any(AddressModel.class));
  }

  @Test
  void createThrows404WhenCityNotFound() {
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(requestFor("missing", "Rua Qualquer")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
    verify(addressRepository, never()).save(any(AddressModel.class));
  }

  @Test
  void updatePersistsFields() {
    AddressModel address = addressWithToken("tok");
    AddressResponseDTO response = responseFor("tok");
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));
    when(cityRepository.findByToken("city-campinas")).thenReturn(Optional.of(city()));
    when(addressRepository.save(address)).thenReturn(address);
    when(mapper.toResponse(address)).thenReturn(response);

    AddressResponseDTO result = service.update("tok", requestFor("city-campinas", "Avenida Nova"));

    assertThat(result).isEqualTo(response);
    assertThat(address.getStreet()).isEqualTo("Avenida Nova");
  }

  @Test
  void updateThrows404WhenAddressNotFound() {
    when(addressRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("missing", requestFor("city-campinas", "Rua X")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void updateThrows404WhenCityNotFound() {
    AddressModel address = addressWithToken("tok");
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));
    when(cityRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("tok", requestFor("missing", "Rua X")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void deleteSoftDeletesAddress() {
    AddressModel address = addressWithToken("tok");
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));

    service.delete("tok");

    verify(addressRepository).delete(address);
  }

  @Test
  void deleteThrows404WhenNotFound() {
    when(addressRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void restoreRecoversDeletedAddress() {
    AddressModel address = addressWithToken("tok");
    AddressResponseDTO response = responseFor("tok");
    when(addressRepository.existsDeletedByToken("tok")).thenReturn(true);
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));
    when(mapper.toResponse(address)).thenReturn(response);

    AddressResponseDTO result = service.restore("tok");

    assertThat(result).isEqualTo(response);
    verify(addressRepository).restoreByToken("tok");
  }

  @Test
  void restoreThrows409WhenAlreadyActive() {
    AddressModel address = addressWithToken("tok");
    when(addressRepository.existsDeletedByToken("tok")).thenReturn(false);
    when(addressRepository.findByToken("tok")).thenReturn(Optional.of(address));

    assertThatThrownBy(() -> service.restore("tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(addressRepository, never()).restoreByToken("tok");
  }

  @Test
  void restoreThrows404WhenNotFound() {
    when(addressRepository.existsDeletedByToken("missing")).thenReturn(false);
    when(addressRepository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.restore("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
