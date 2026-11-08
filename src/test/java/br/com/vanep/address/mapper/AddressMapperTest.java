package br.com.vanep.address.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.state.model.StateModel;
import org.junit.jupiter.api.Test;

class AddressMapperTest {
  private final AddressMapper mapper = new AddressMapper();

  private AddressModel address() {
    StateModel state = new StateModel();
    state.setUf("SP");
    CityModel city = new CityModel();
    city.setToken("city-campinas");
    city.setName("Campinas");
    city.setState(state);
    AddressModel address = new AddressModel();
    address.setToken("addr-tok");
    address.setCity(city);
    address.setStreet("Rua Barão de Jaguara");
    address.setZipCode("13015904");
    return address;
  }

  @Test
  void exposesTheNeighborhoodWhenStored() {
    AddressModel address = address();
    address.setNeighborhood("Centro");

    AddressResponseDTO response = mapper.toResponse(address);

    assertThat(response.neighborhood()).isEqualTo("Centro");
    assertThat(response.cityToken()).isEqualTo("city-campinas");
    assertThat(response.stateUf()).isEqualTo("SP");
  }

  @Test
  void leavesTheNeighborhoodNullWhenNoneWasStored() {
    assertThat(mapper.toResponse(address()).neighborhood()).isNull();
  }
}
