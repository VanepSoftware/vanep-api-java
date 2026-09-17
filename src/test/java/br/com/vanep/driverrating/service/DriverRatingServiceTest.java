package br.com.vanep.driverrating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.dto.DriverRatingCreateRequestDTO;
import br.com.vanep.driverrating.dto.DriverRatingResponseDTO;
import br.com.vanep.driverrating.dto.DriverRatingUpdateRequestDTO;
import br.com.vanep.driverrating.mapper.DriverRatingMapper;
import br.com.vanep.driverrating.model.DriverRatingModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
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
class DriverRatingServiceTest {

  @Mock private DriverRatingRepository driverRatingRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private UserRepository userRepository;
  @Mock private DriverRatingMapper mapper;
  @Mock private MessageSource messages;

  private DriverRatingService service;

  @BeforeEach
  void setUp() {
    service =
        new DriverRatingService(
            driverRatingRepository,
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
    caller.setId(10L);
    caller.setEmail("client@vanep.com");

    ClientModel client = mockClient(1L, 10L);
    DriverModel driver = mockDriver(2L, 20L);

    DriverRatingCreateRequestDTO dto =
        new DriverRatingCreateRequestDTO("driver-token", BigDecimal.valueOf(5.00), "Great!");
    DriverRatingResponseDTO expectedResponse =
        new DriverRatingResponseDTO(
            "rating-token",
            "driver-token",
            "Driver Name",
            "client-token",
            "Client Name",
            BigDecimal.valueOf(5.00),
            "Great!",
            null,
            null);

    when(userRepository.findByEmail("client@vanep.com")).thenReturn(Optional.of(caller));
    when(clientRepository.findByUserId(10L)).thenReturn(Optional.of(client));
    when(driverRepository.findByToken("driver-token")).thenReturn(Optional.of(driver));
    when(driverRatingRepository.existsByDriverIdAndClientId(2L, 1L)).thenReturn(false);
    when(driverRatingRepository.save(any(DriverRatingModel.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(driverRatingRepository.calculateAverageRatingForDriver(2L))
        .thenReturn(Optional.of(BigDecimal.valueOf(5.00)));
    when(mapper.toResponse(any())).thenReturn(expectedResponse);

    DriverRatingResponseDTO response = service.create(dto, "client@vanep.com");

    assertThat(response).isEqualTo(expectedResponse);
    verify(driverRepository).save(driver);
  }

  @Test
  void createThrowsBadRequestWhenSelfRating() {
    UserModel caller = new UserModel();
    caller.setId(10L);
    caller.setEmail("driver@vanep.com");

    ClientModel client = mockClient(1L, 10L);
    DriverModel driver = mockDriver(2L, 10L); // same user ID!

    DriverRatingCreateRequestDTO dto =
        new DriverRatingCreateRequestDTO("driver-token", BigDecimal.valueOf(5.00), "Self");

    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(caller));
    when(clientRepository.findByUserId(10L)).thenReturn(Optional.of(client));
    when(driverRepository.findByToken("driver-token")).thenReturn(Optional.of(driver));
    when(messages.getMessage(anyString(), any(), any())).thenReturn("Cannot rate self");

    assertThatThrownBy(() -> service.create(dto, "driver@vanep.com"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void createThrowsConflictWhenDuplicate() {
    UserModel caller = new UserModel();
    caller.setId(10L);
    caller.setEmail("client@vanep.com");

    ClientModel client = mockClient(1L, 10L);
    DriverModel driver = mockDriver(2L, 20L);

    DriverRatingCreateRequestDTO dto =
        new DriverRatingCreateRequestDTO("driver-token", BigDecimal.valueOf(5.00), "Again");

    when(userRepository.findByEmail("client@vanep.com")).thenReturn(Optional.of(caller));
    when(clientRepository.findByUserId(10L)).thenReturn(Optional.of(client));
    when(driverRepository.findByToken("driver-token")).thenReturn(Optional.of(driver));
    when(driverRatingRepository.existsByDriverIdAndClientId(2L, 1L)).thenReturn(true);
    when(messages.getMessage(anyString(), any(), any())).thenReturn("Duplicate rating");

    assertThatThrownBy(() -> service.create(dto, "client@vanep.com"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void findAllReturnsPagedList() {
    DriverRatingModel rating = new DriverRatingModel();
    DriverRatingResponseDTO response =
        new DriverRatingResponseDTO(
            "tok", "dtok", "DName", "ctok", "CName", BigDecimal.valueOf(4.5), "Good", null, null);

    when(driverRatingRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(rating)));
    when(mapper.toResponse(rating)).thenReturn(response);

    var result = service.findAll(null, Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findAllFilteredByDriverToken() {
    DriverRatingModel rating = new DriverRatingModel();
    DriverRatingResponseDTO response =
        new DriverRatingResponseDTO(
            "tok", "dtok", "DName", "ctok", "CName", BigDecimal.valueOf(4.5), "Good", null, null);

    when(driverRatingRepository.findByDriverToken("dtok", Pageable.unpaged()))
        .thenReturn(new PageImpl<>(List.of(rating)));
    when(mapper.toResponse(rating)).thenReturn(response);

    var result = service.findAll("dtok", Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    DriverRatingModel rating = new DriverRatingModel();
    DriverRatingResponseDTO response =
        new DriverRatingResponseDTO(
            "tok", "dtok", "DName", "ctok", "CName", BigDecimal.valueOf(4.5), "Good", null, null);

    when(driverRatingRepository.findByToken("tok")).thenReturn(Optional.of(rating));
    when(mapper.toResponse(rating)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void updatePersistsChangesAndRecalculatesAverage() {
    DriverModel driver = mockDriver(2L, 20L);
    DriverRatingModel ratingModel = new DriverRatingModel();
    ratingModel.setDriver(driver);

    DriverRatingUpdateRequestDTO dto =
        new DriverRatingUpdateRequestDTO(BigDecimal.valueOf(4.00), "Updated comment");
    DriverRatingResponseDTO response =
        new DriverRatingResponseDTO(
            "tok",
            "dtok",
            "DName",
            "ctok",
            "CName",
            BigDecimal.valueOf(4.0),
            "Updated comment",
            null,
            null);

    when(driverRatingRepository.findByToken("tok")).thenReturn(Optional.of(ratingModel));
    when(driverRatingRepository.save(ratingModel)).thenReturn(ratingModel);
    when(driverRatingRepository.calculateAverageRatingForDriver(2L))
        .thenReturn(Optional.of(BigDecimal.valueOf(4.00)));
    when(mapper.toResponse(ratingModel)).thenReturn(response);

    DriverRatingResponseDTO result = service.update("tok", dto);

    assertThat(result).isEqualTo(response);
    verify(driverRepository).save(driver);
  }

  @Test
  void deleteRemovesRatingAndRecalculatesAverage() {
    DriverModel driver = mockDriver(2L, 20L);
    DriverRatingModel ratingModel = new DriverRatingModel();
    ratingModel.setDriver(driver);

    when(driverRatingRepository.findByToken("tok")).thenReturn(Optional.of(ratingModel));
    when(driverRatingRepository.calculateAverageRatingForDriver(2L))
        .thenReturn(Optional.of(BigDecimal.valueOf(5.00)));

    service.delete("tok");

    verify(driverRatingRepository).delete(ratingModel);
    verify(driverRepository).save(driver);
  }

  @Test
  void restoreRestoresDeletedRating() {
    DriverModel driver = mockDriver(2L, 20L);
    DriverRatingModel ratingModel = new DriverRatingModel();
    ratingModel.setDriver(driver);
    DriverRatingResponseDTO response =
        new DriverRatingResponseDTO(
            "tok", "dtok", "DName", "ctok", "CName", BigDecimal.valueOf(5.0), "Good", null, null);

    when(driverRatingRepository.existsDeletedByToken("tok")).thenReturn(true);
    when(driverRatingRepository.findByToken("tok")).thenReturn(Optional.of(ratingModel));
    when(driverRatingRepository.calculateAverageRatingForDriver(2L))
        .thenReturn(Optional.of(BigDecimal.valueOf(5.00)));
    when(mapper.toResponse(ratingModel)).thenReturn(response);

    DriverRatingResponseDTO result = service.restore("tok");

    verify(driverRatingRepository).restoreByToken("tok");
    assertThat(result).isEqualTo(response);
  }
}
