package br.com.vanep.unlinkedpassenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.address.dto.DependentAddressRequestDTO;
import br.com.vanep.address.mapper.AddressMapper;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.schedule.mapper.ScheduleMapper;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.schedule.repository.ScheduleRepository;
import br.com.vanep.schedule.service.ScheduleService;
import br.com.vanep.schedule.service.ScheduleSlotPolicy;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerCreateRequestDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerResponseDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerScheduleRequestDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerUpdateRequestDTO;
import br.com.vanep.unlinkedpassenger.mapper.UnlinkedPassengerMapper;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnlinkedPassengerServiceTest {

  private static final String CALLER = "carlos-uid";

  @Mock private UnlinkedPassengerRepository passengers;
  @Mock private DriverRepository drivers;
  @Mock private UserService users;
  @Mock private SchoolRepository schools;
  @Mock private AddressService addresses;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private MessageSource messages;

  @Captor private ArgumentCaptor<UnlinkedPassengerModel> savedPassenger;

  private UnlinkedPassengerService service;
  private DriverModel carlos;
  private SchoolModel school;
  private CityModel campinas;

  @BeforeEach
  void setUp() {
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));
    when(passengers.save(any())).thenAnswer(call -> call.getArgument(0));
    when(scheduleRepository.save(any())).thenAnswer(call -> call.getArgument(0));

    service =
        new UnlinkedPassengerService(
            passengers,
            drivers,
            users,
            schools,
            addresses,
            new ScheduleService(scheduleRepository, new ScheduleSlotPolicy(), messages),
            new UnlinkedPassengerMapper(new AddressMapper(), new ScheduleMapper()),
            messages);

    UserModel user = new UserModel();
    user.setId(1L);
    user.setType(UserType.DRIVER);
    when(users.requireByTokenAndType(CALLER, UserType.DRIVER)).thenReturn(user);
    carlos = new DriverModel();
    carlos.setId(2L);
    carlos.setApprovalStatus(DriverApprovalStatus.APPROVED);
    when(drivers.findByUserId(1L)).thenReturn(Optional.of(carlos));

    school = new SchoolModel();
    school.setId(7L);
    school.setToken("school");
    school.setName("Escola Municipal");
    when(schools.findByToken("school")).thenReturn(Optional.of(school));

    StateModel sp = new StateModel();
    sp.setUf("SP");
    campinas = new CityModel();
    campinas.setToken("campinas");
    campinas.setName("Campinas");
    campinas.setState(sp);
    doAnswer(
            call -> {
              UnlinkedPassengerModel passenger = call.getArgument(0);
              DependentAddressRequestDTO request = call.getArgument(1);
              passenger.setAddress(address(request.street()));
              return null;
            })
        .when(addresses)
        .upsertForUnlinkedPassenger(any(), any());
  }

  @Test
  void createsAPassengerWithTheAddressAndTheScheduleForTheCallingDriver() {
    UnlinkedPassengerResponseDTO response = service.create(CALLER, createRequest());

    verify(passengers).save(savedPassenger.capture());
    UnlinkedPassengerModel passenger = savedPassenger.getValue();
    assertThat(passenger.getDriver()).isSameAs(carlos);
    assertThat(passenger.getSchool()).isSameAs(school);
    assertThat(passenger.getSchoolShift()).isEqualTo(SchoolShift.MORNING);
    assertThat(passenger.getAddress().getStreet()).isEqualTo("Rua das Flores");
    assertThat(passenger.getSchedule().getSlots()).hasSize(2);
    assertThat(response.name()).isEqualTo("Lucas");
    assertThat(response.schoolToken()).isEqualTo("school");
    assertThat(response.address().street()).isEqualTo("Rua das Flores");
    assertThat(response.slots())
        .extracting(slot -> slot.leg())
        .containsExactly(RouteLeg.OUTBOUND, RouteLeg.RETURN);
  }

  @Test
  void aDriverStillInReviewCanRegisterPassengers() {
    carlos.setApprovalStatus(DriverApprovalStatus.PENDING);

    service.create(CALLER, createRequest());

    verify(passengers).save(any());
  }

  @Test
  void aCallerThatIsNotADriverIsForbidden() {
    when(users.requireByTokenAndType("maria-uid", UserType.DRIVER))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "user.type.forbidden"));

    assertThatThrownBy(() -> service.create("maria-uid", createRequest()))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(passengers, never()).save(any());
  }

  @Test
  void anUnknownSchoolIsNotFoundAndNothingIsSaved() {
    UnlinkedPassengerCreateRequestDTO request =
        new UnlinkedPassengerCreateRequestDTO(
            "Lucas",
            "missing",
            SchoolShift.MORNING,
            addressRequest("Rua das Flores"),
            null,
            slots());

    assertThatThrownBy(() -> service.create(CALLER, request))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(exception.getReason()).isEqualTo("unlinked_passenger.school.not_found");
            });
    verify(passengers, never()).save(any());
  }

  @Test
  void anInvalidScheduleIsRejectedBeforeTheAddressIsWritten() {
    UnlinkedPassengerCreateRequestDTO request =
        new UnlinkedPassengerCreateRequestDTO(
            "Lucas",
            "school",
            SchoolShift.MORNING,
            addressRequest("Rua das Flores"),
            null,
            List.of());

    assertThatThrownBy(() -> service.create(CALLER, request))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(exception.getReason()).isEqualTo("schedule.slots.required");
            });
    verifyNoInteractions(addresses);
    verify(passengers, never()).save(any());
  }

  @Test
  void listsOnlyTheCallingDriversPassengers() {
    when(passengers.findByDriverId(2L)).thenReturn(List.of(passenger("lucas")));

    List<UnlinkedPassengerResponseDTO> response = service.findMine(CALLER);

    assertThat(response).extracting(passenger -> passenger.token()).containsExactly("lucas");
  }

  @Test
  void readsOneOfTheCallingDriversPassengers() {
    when(passengers.findByTokenAndDriverId("lucas", 2L))
        .thenReturn(Optional.of(passenger("lucas")));

    assertThat(service.findMineByToken(CALLER, "lucas").token()).isEqualTo("lucas");
  }

  @Test
  void anotherDriversPassengerIsNotFound() {
    when(passengers.findByTokenAndDriverId("someone-else", 2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findMineByToken(CALLER, "someone-else"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(exception.getReason()).isEqualTo("unlinked_passenger.not_found");
            });
  }

  @Test
  void aNotesOnlyPatchKeepsEveryOtherField() {
    UnlinkedPassengerModel passenger = passenger("lucas");
    AddressModel address = passenger.getAddress();
    ScheduleModel schedule = passenger.getSchedule();
    List<ScheduleSlotModel> slots = List.copyOf(schedule.getSlots());
    when(passengers.findByTokenAndDriverId("lucas", 2L)).thenReturn(Optional.of(passenger));

    service.update(CALLER, "lucas", patch().withNotes(JsonNullable.of("Portão azul")));

    assertThat(passenger.getNotes()).isEqualTo("Portão azul");
    assertThat(passenger.getName()).isEqualTo("Lucas");
    assertThat(passenger.getSchool()).isSameAs(school);
    assertThat(passenger.getSchoolShift()).isEqualTo(SchoolShift.MORNING);
    assertThat(passenger.getAddress()).isSameAs(address);
    assertThat(passenger.getSchedule()).isSameAs(schedule);
    assertThat(schedule.getSlots()).containsExactlyElementsOf(slots);
    verifyNoInteractions(addresses);
  }

  @Test
  void aPatchChangesTheNameTheSchoolAndTheShift() {
    UnlinkedPassengerModel passenger = passenger("lucas");
    SchoolModel otherSchool = new SchoolModel();
    otherSchool.setToken("other-school");
    when(schools.findByToken("other-school")).thenReturn(Optional.of(otherSchool));
    when(passengers.findByTokenAndDriverId("lucas", 2L)).thenReturn(Optional.of(passenger));

    service.update(
        CALLER,
        "lucas",
        new UnlinkedPassengerUpdateRequestDTO(
            JsonNullable.of("Lucas Silva"),
            JsonNullable.of("other-school"),
            JsonNullable.of(SchoolShift.AFTERNOON),
            null,
            null));

    assertThat(passenger.getName()).isEqualTo("Lucas Silva");
    assertThat(passenger.getSchool()).isSameAs(otherSchool);
    assertThat(passenger.getSchoolShift()).isEqualTo(SchoolShift.AFTERNOON);
  }

  @Test
  void aPatchWithAnAddressRewritesThePassengersAddress() {
    UnlinkedPassengerModel passenger = passenger("lucas");
    when(passengers.findByTokenAndDriverId("lucas", 2L)).thenReturn(Optional.of(passenger));
    DependentAddressRequestDTO newAddress = addressRequest("Avenida Nova");

    service.update(CALLER, "lucas", patch().withAddress(JsonNullable.of(newAddress)));

    verify(addresses).upsertForUnlinkedPassenger(passenger, newAddress);
    assertThat(passenger.getAddress().getStreet()).isEqualTo("Avenida Nova");
  }

  @Test
  void aNullNotesClearsThem() {
    UnlinkedPassengerModel passenger = passenger("lucas");
    passenger.setNotes("Portão azul");
    when(passengers.findByTokenAndDriverId("lucas", 2L)).thenReturn(Optional.of(passenger));

    service.update(CALLER, "lucas", patch().withNotes(JsonNullable.of(null)));

    assertThat(passenger.getNotes()).isNull();
  }

  @Test
  void aNullOrBlankNameIsRejected() {
    when(passengers.findByTokenAndDriverId("lucas", 2L))
        .thenReturn(Optional.of(passenger("lucas")));

    assertBadRequest(patch().withName(JsonNullable.of(null)), "unlinked_passenger.name.required");
    assertBadRequest(patch().withName(JsonNullable.of("  ")), "unlinked_passenger.name.required");
  }

  @Test
  void aNullSchoolShiftOrAddressIsRejected() {
    when(passengers.findByTokenAndDriverId("lucas", 2L))
        .thenReturn(Optional.of(passenger("lucas")));

    assertBadRequest(
        patch().withSchoolToken(JsonNullable.of(null)), "unlinked_passenger.field.null");
    assertBadRequest(
        patch().withSchoolShift(JsonNullable.of(null)), "unlinked_passenger.field.null");
    assertBadRequest(patch().withAddress(JsonNullable.of(null)), "unlinked_passenger.field.null");
    verify(passengers, never()).save(any());
  }

  @Test
  void replacingTheScheduleSwapsTheSlotsOfTheSameSchedule() {
    UnlinkedPassengerModel passenger = passenger("lucas");
    ScheduleModel schedule = passenger.getSchedule();
    when(passengers.findByTokenAndDriverId("lucas", 2L)).thenReturn(Optional.of(passenger));

    UnlinkedPassengerResponseDTO response =
        service.replaceSchedule(
            CALLER,
            "lucas",
            new UnlinkedPassengerScheduleRequestDTO(
                List.of(slot(DayOfWeek.FRIDAY, RouteLeg.OUTBOUND, LocalTime.of(7, 0)))));

    assertThat(passenger.getSchedule()).isSameAs(schedule);
    assertThat(schedule.getSlots())
        .extracting(slot -> slot.getWeekday())
        .containsExactly(DayOfWeek.FRIDAY);
    assertThat(response.slots()).hasSize(1);
    verify(scheduleRepository).flush();
  }

  @Test
  void deletingSoftDeletesThePassengerBeforeFreeingItsAddress() {
    UnlinkedPassengerModel passenger = passenger("lucas");
    when(passengers.findByTokenAndDriverId("lucas", 2L)).thenReturn(Optional.of(passenger));

    service.delete(CALLER, "lucas");

    InOrder order = inOrder(passengers, addresses);
    order.verify(passengers).delete(passenger);
    order.verify(addresses).clearForUnlinkedPassenger(passenger);
  }

  @Test
  void deletingAnotherDriversPassengerIsNotFound() {
    when(passengers.findByTokenAndDriverId("someone-else", 2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(CALLER, "someone-else"))
        .isInstanceOf(ResponseStatusException.class);
    verify(passengers, never()).delete(any());
    verifyNoInteractions(addresses);
  }

  void assertBadRequest(UnlinkedPassengerUpdateRequestDTO request, String key) {
    assertThatThrownBy(() -> service.update(CALLER, "lucas", request))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(exception.getReason()).isEqualTo(key);
            });
  }

  UnlinkedPassengerCreateRequestDTO createRequest() {
    return new UnlinkedPassengerCreateRequestDTO(
        "Lucas", "school", SchoolShift.MORNING, addressRequest("Rua das Flores"), null, slots());
  }

  PatchBuilder patch() {
    return new PatchBuilder(null, null, null, null, null);
  }

  record PatchBuilder(
      JsonNullable<String> name,
      JsonNullable<String> schoolToken,
      JsonNullable<SchoolShift> schoolShift,
      JsonNullable<DependentAddressRequestDTO> address,
      JsonNullable<String> notes) {

    UnlinkedPassengerUpdateRequestDTO withName(JsonNullable<String> value) {
      return new UnlinkedPassengerUpdateRequestDTO(value, schoolToken, schoolShift, address, notes);
    }

    UnlinkedPassengerUpdateRequestDTO withSchoolToken(JsonNullable<String> value) {
      return new UnlinkedPassengerUpdateRequestDTO(name, value, schoolShift, address, notes);
    }

    UnlinkedPassengerUpdateRequestDTO withSchoolShift(JsonNullable<SchoolShift> value) {
      return new UnlinkedPassengerUpdateRequestDTO(name, schoolToken, value, address, notes);
    }

    UnlinkedPassengerUpdateRequestDTO withAddress(JsonNullable<DependentAddressRequestDTO> value) {
      return new UnlinkedPassengerUpdateRequestDTO(name, schoolToken, schoolShift, value, notes);
    }

    UnlinkedPassengerUpdateRequestDTO withNotes(JsonNullable<String> value) {
      return new UnlinkedPassengerUpdateRequestDTO(name, schoolToken, schoolShift, address, value);
    }
  }

  UnlinkedPassengerModel passenger(String token) {
    UnlinkedPassengerModel passenger = new UnlinkedPassengerModel();
    passenger.setToken(token);
    passenger.setDriver(carlos);
    passenger.setName("Lucas");
    passenger.setSchool(school);
    passenger.setSchoolShift(SchoolShift.MORNING);
    passenger.setAddress(address("Rua das Flores"));
    ScheduleModel schedule = new ScheduleModel();
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(DayOfWeek.MONDAY);
    slot.setLeg(RouteLeg.OUTBOUND);
    slot.setShift(OperationShift.MORNING);
    slot.setWindowStart(LocalTime.of(6, 40));
    schedule.addSlot(slot);
    passenger.setSchedule(schedule);
    return passenger;
  }

  AddressModel address(String street) {
    AddressModel address = new AddressModel();
    address.setToken("address");
    address.setCity(campinas);
    address.setZipCode("13015904");
    address.setStreet(street);
    address.setNumber("100");
    return address;
  }

  DependentAddressRequestDTO addressRequest(String street) {
    return new DependentAddressRequestDTO("campinas", street, "13015904", "100", null, "Centro");
  }

  List<ScheduleSlotRequestDTO> slots() {
    return List.of(
        new ScheduleSlotRequestDTO(
            DayOfWeek.MONDAY,
            RouteLeg.RETURN,
            OperationShift.AFTERNOON,
            LocalTime.of(12, 10),
            null),
        slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, LocalTime.of(6, 40)));
  }

  ScheduleSlotRequestDTO slot(DayOfWeek weekday, RouteLeg leg, LocalTime windowStart) {
    return new ScheduleSlotRequestDTO(weekday, leg, OperationShift.MORNING, windowStart, null);
  }
}
