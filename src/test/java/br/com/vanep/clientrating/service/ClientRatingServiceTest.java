package br.com.vanep.clientrating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientrating.dto.ClientRatingCreateRequestDTO;
import br.com.vanep.clientrating.dto.ClientRatingResponseDTO;
import br.com.vanep.clientrating.mapper.ClientRatingMapper;
import br.com.vanep.clientrating.model.ClientRatingModel;
import br.com.vanep.clientrating.repository.ClientRatingRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
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
class ClientRatingServiceTest {

  @Mock private ClientRatingRepository clientRatingRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private UserRepository userRepository;
  @Mock private ClientRatingMapper mapper;
  @Mock private MessageSource messages;

  private ClientRatingService service;

  @BeforeEach
  void setUp() {
    service =
        new ClientRatingService(
            clientRatingRepository,
            driverRepository,
            clientRepository,
            userRepository,
            mapper,
            messages);
  }

  private DriverModel mockDriver(Long id, Long userId) {
    UserModel user = new UserModel();
    user.setId(userId);
    user.setName("Driver Name");
    user.setEmail("driver@vanep.com");

    DriverModel driver = new DriverModel();
    driver.setId(id);
    driver.setToken("driver-token");
    driver.setUser(user);
    return driver;
  }

  private ClientModel mockClient(Long id, Long userId) {
    UserModel user = new UserModel();
    user.setId(userId);
    user.setName("Client Name");
    user.setEmail("client@vanep.com");

    ClientModel client = new ClientModel();
    client.setId(id);
    client.setToken("client-token");
    client.setUser(user);
    return client;
  }

  @Test
  void createSuccessfully() {
    UserModel caller = new UserModel();
    caller.setId(20L);
    caller.setEmail("driver@vanep.com");

    DriverModel driver = mockDriver(2L, 20L);
    ClientModel client = mockClient(1L, 10L);

    ClientRatingCreateRequestDTO dto =
        new ClientRatingCreateRequestDTO("client-token", BigDecimal.valueOf(5.00), "Great!");
    ClientRatingResponseDTO expectedResponse =
        new ClientRatingResponseDTO(
            "rating-token",
            "driver-token",
            "Driver Name",
            "client-token",
            "Client Name",
            BigDecimal.valueOf(5.00),
            "Great!",
            null,
            null);

    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(caller));
    when(driverRepository.findByUserId(20L)).thenReturn(Optional.of(driver));
    when(clientRepository.findByToken("client-token")).thenReturn(Optional.of(client));
    when(clientRatingRepository.existsByDriverIdAndClientId(2L, 1L)).thenReturn(false);
    when(clientRatingRepository.save(any(ClientRatingModel.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(clientRatingRepository.calculateAverageRatingForClient(1L))
        .thenReturn(Optional.of(BigDecimal.valueOf(5.00)));
    when(mapper.toResponse(any())).thenReturn(expectedResponse);

    ClientRatingResponseDTO response = service.create(dto, "driver@vanep.com");

    assertThat(response).isEqualTo(expectedResponse);
    verify(clientRepository).save(client);
  }

  @Test
  void createThrowsBadRequestWhenSelfRating() {
    UserModel caller = new UserModel();
    caller.setId(10L);
    caller.setEmail("client@vanep.com");

    DriverModel driver = mockDriver(2L, 10L);
    ClientModel client = mockClient(1L, 10L); // same user ID!

    ClientRatingCreateRequestDTO dto =
        new ClientRatingCreateRequestDTO("client-token", BigDecimal.valueOf(5.00), "Self");

    when(userRepository.findByEmail("client@vanep.com")).thenReturn(Optional.of(caller));
    when(driverRepository.findByUserId(10L)).thenReturn(Optional.of(driver));
    when(clientRepository.findByToken("client-token")).thenReturn(Optional.of(client));
    when(messages.getMessage(anyString(), any(), any())).thenReturn("Cannot rate self");

    assertThatThrownBy(() -> service.create(dto, "client@vanep.com"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void createThrowsConflictWhenDuplicate() {
    UserModel caller = new UserModel();
    caller.setId(20L);
    caller.setEmail("driver@vanep.com");

    DriverModel driver = mockDriver(2L, 20L);
    ClientModel client = mockClient(1L, 10L);

    ClientRatingCreateRequestDTO dto =
        new ClientRatingCreateRequestDTO("client-token", BigDecimal.valueOf(5.00), "Again");

    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(caller));
    when(driverRepository.findByUserId(20L)).thenReturn(Optional.of(driver));
    when(clientRepository.findByToken("client-token")).thenReturn(Optional.of(client));
    when(clientRatingRepository.existsByDriverIdAndClientId(2L, 1L)).thenReturn(true);
    when(messages.getMessage(anyString(), any(), any())).thenReturn("Duplicate rating");

    assertThatThrownBy(() -> service.create(dto, "driver@vanep.com"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void findAllReturnsPagedList() {
    ClientRatingModel rating = new ClientRatingModel();
    ClientRatingResponseDTO response =
        new ClientRatingResponseDTO(
            "tok", "dtok", "DName", "ctok", "CName", BigDecimal.valueOf(4.5), "Good", null, null);

    when(clientRatingRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(rating)));
    when(mapper.toResponse(rating)).thenReturn(response);

    var result = service.findAll(null, Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findAllFilteredByClientToken() {
    ClientRatingModel rating = new ClientRatingModel();
    ClientRatingResponseDTO response =
        new ClientRatingResponseDTO(
            "tok", "dtok", "DName", "ctok", "CName", BigDecimal.valueOf(4.5), "Good", null, null);

    when(clientRatingRepository.findByClientToken("ctok", Pageable.unpaged()))
        .thenReturn(new PageImpl<>(List.of(rating)));
    when(mapper.toResponse(rating)).thenReturn(response);

    var result = service.findAll("ctok", Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    ClientRatingModel rating = new ClientRatingModel();
    ClientRatingResponseDTO response =
        new ClientRatingResponseDTO(
            "tok", "dtok", "DName", "ctok", "CName", BigDecimal.valueOf(4.5), "Good", null, null);

    when(clientRatingRepository.findByToken("tok")).thenReturn(Optional.of(rating));
    when(mapper.toResponse(rating)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void deleteRemovesRatingAndRecalculatesAverage() {
    ClientModel client = mockClient(1L, 10L);
    ClientRatingModel ratingModel = new ClientRatingModel();
    ratingModel.setClient(client);

    when(clientRatingRepository.findByToken("tok")).thenReturn(Optional.of(ratingModel));
    when(clientRatingRepository.calculateAverageRatingForClient(1L))
        .thenReturn(Optional.of(BigDecimal.valueOf(5.00)));

    service.delete("tok");

    verify(clientRatingRepository).delete(ratingModel);
    verify(clientRepository).save(client);
  }
}
