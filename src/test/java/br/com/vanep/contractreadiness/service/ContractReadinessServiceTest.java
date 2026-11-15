package br.com.vanep.contractreadiness.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.contractreadiness.dto.ContractReadinessMissingDTO;
import br.com.vanep.contractreadiness.dto.ContractReadinessResponseDTO;
import br.com.vanep.contractreadiness.enums.ContractReadinessField;
import br.com.vanep.contractreadiness.enums.ContractReadinessSubject;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ContractReadinessServiceTest {
  private static final String CALLER = "ana-uid";
  private static final Long CLIENT_ID = 10L;

  @Mock private UserService users;
  @Mock private ClientRepository clients;
  @Mock private DependentRepository dependents;
  @Mock private DependentSchoolDayRepository schoolDays;
  @Mock private MessageSource messages;

  private ContractReadinessService service;
  private UserModel ana;

  @BeforeEach
  void setUp() {
    service =
        new ContractReadinessService(
            users, clients, dependents, schoolDays, new ContractReadinessPolicy(), messages);
    lenient()
        .when(messages.getMessage(any(), any(), any()))
        .thenAnswer(call -> call.getArgument(0));
    ana = new UserModel();
    ana.setId(1L);
    ana.setDocument("11144477735");
    ana.setPhone("11999990000");
    ana.setAddressId(5L);
    ClientModel client = new ClientModel();
    client.setId(CLIENT_ID);
    lenient().when(users.requireByTokenAndType(CALLER, UserType.CLIENT)).thenReturn(ana);
    lenient().when(clients.findByUserId(1L)).thenReturn(Optional.of(client));
  }

  @Test
  void reportsWhatIsMissingInTheOrderOfTheRequestedDependents() {
    ana.setPhone(null);
    DependentModel lucas = dependent(21L, "lucas", LocalDate.of(2016, 4, 2), 7L, null);
    DependentModel marina = dependent(22L, "marina", null, 7L, 9L);
    when(dependents.findByTokenInAndClientId(List.of("marina", "lucas"), CLIENT_ID))
        .thenReturn(List.of(lucas, marina));
    when(schoolDays.findByDependentIdIn(List.of(22L, 21L)))
        .thenReturn(List.of(schoolDay(21L, DayOfWeek.MONDAY)));

    ContractReadinessResponseDTO readiness =
        service.findReadiness(CALLER, List.of("marina", "lucas"));

    assertThat(readiness.ready()).isFalse();
    assertThat(readiness.missing())
        .containsExactly(
            ofClient(ContractReadinessField.PHONE),
            ofDependent("marina", ContractReadinessField.BIRTH_DATE),
            ofDependent("marina", ContractReadinessField.SCHOOL_DAYS),
            ofDependent("lucas", ContractReadinessField.ADDRESS));
  }

  @Test
  void isReadyWhenNothingIsMissing() {
    DependentModel lucas = dependent(21L, "lucas", LocalDate.of(2016, 4, 2), 7L, 9L);
    when(dependents.findByTokenInAndClientId(List.of("lucas"), CLIENT_ID))
        .thenReturn(List.of(lucas));
    when(schoolDays.findByDependentIdIn(List.of(21L)))
        .thenReturn(List.of(schoolDay(21L, DayOfWeek.MONDAY)));

    ContractReadinessResponseDTO readiness = service.findReadiness(CALLER, List.of("lucas"));

    assertThat(readiness.ready()).isTrue();
    assertThat(readiness.missing()).isEmpty();
  }

  @Test
  void checksATokenSentTwiceOnce() {
    DependentModel lucas = dependent(21L, "lucas", null, 7L, 9L);
    when(dependents.findByTokenInAndClientId(List.of("lucas"), CLIENT_ID))
        .thenReturn(List.of(lucas));
    when(schoolDays.findByDependentIdIn(List.of(21L)))
        .thenReturn(List.of(schoolDay(21L, DayOfWeek.MONDAY)));

    ContractReadinessResponseDTO readiness =
        service.findReadiness(CALLER, List.of("lucas", "lucas"));

    assertThat(readiness.missing())
        .containsExactly(ofDependent("lucas", ContractReadinessField.BIRTH_DATE));
  }

  @Test
  void checksOnlyTheClientWithoutDependentTokens() {
    ana.setAddressId(null);

    ContractReadinessResponseDTO readiness = service.findReadiness(CALLER, List.of());

    assertThat(readiness.missing()).containsExactly(ofClient(ContractReadinessField.ADDRESS));
    verifyNoInteractions(dependents, schoolDays);
  }

  @Test
  void aTokenThatIsNotAnActiveDependentOfTheCallerIsNotFound() {
    when(dependents.findByTokenInAndClientId(List.of("lucas", "other"), CLIENT_ID))
        .thenReturn(List.of(dependent(21L, "lucas", null, null, null)));

    assertThatThrownBy(() -> service.findReadiness(CALLER, List.of("lucas", "other")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verifyNoInteractions(schoolDays);
  }

  @Test
  void onlyAClientCanAskForTheReadiness() {
    when(users.requireByTokenAndType("driver-uid", UserType.CLIENT))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "user.type.forbidden"));

    assertThatThrownBy(() -> service.findReadiness("driver-uid", List.of("lucas")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verify(users).requireByTokenAndType("driver-uid", UserType.CLIENT);
    verifyNoInteractions(dependents);
  }

  @Test
  void theServiceRequestChecksTheDependentsAndGridsItAlreadyLoaded() {
    DependentModel lucas = dependent(21L, "lucas", LocalDate.of(2016, 4, 2), 7L, 9L);

    assertThat(service.isReady(ana, List.of(lucas), List.of(schoolDay(21L, DayOfWeek.MONDAY))))
        .isTrue();
    assertThat(service.isReady(ana, List.of(lucas), List.of())).isFalse();
    ana.setPhone(null);
    assertThat(service.isReady(ana, List.of(lucas), List.of(schoolDay(21L, DayOfWeek.MONDAY))))
        .isFalse();
    verifyNoInteractions(dependents, schoolDays);
  }

  private DependentModel dependent(
      Long id, String token, LocalDate birthDate, Long schoolId, Long addressId) {
    DependentModel dependent = new DependentModel();
    dependent.setId(id);
    dependent.setToken(token);
    dependent.setClientId(CLIENT_ID);
    dependent.setBirthDate(birthDate);
    dependent.setSchoolId(schoolId);
    dependent.setAddressId(addressId);
    return dependent;
  }

  private DependentSchoolDayModel schoolDay(Long dependentId, DayOfWeek weekday) {
    DependentSchoolDayModel day = new DependentSchoolDayModel();
    day.setDependentId(dependentId);
    day.setWeekday(weekday);
    day.setSchoolShift(SchoolShift.MORNING);
    return day;
  }

  private ContractReadinessMissingDTO ofClient(ContractReadinessField field) {
    return new ContractReadinessMissingDTO(ContractReadinessSubject.CLIENT, null, field);
  }

  private ContractReadinessMissingDTO ofDependent(String token, ContractReadinessField field) {
    return new ContractReadinessMissingDTO(ContractReadinessSubject.DEPENDENT, token, field);
  }
}
