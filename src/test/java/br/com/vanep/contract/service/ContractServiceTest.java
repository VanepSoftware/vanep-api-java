package br.com.vanep.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.clientdriver.service.LinkStatusPolicy;
import br.com.vanep.contract.dto.ContractCreateRequestDTO;
import br.com.vanep.contract.dto.ContractItemRequestDTO;
import br.com.vanep.contract.dto.ContractResponseDTO;
import br.com.vanep.contract.dto.ContractUpdateRequestDTO;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.mapper.ContractMapper;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverApprovalStatus;
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
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractServiceTest {

  private static final LocalDate STARTS_ON = LocalDate.of(2027, 2, 1);
  private static final LocalDate ENDS_ON = LocalDate.of(2027, 12, 15);

  @Mock private ContractRepository contracts;
  @Mock private ContractItemRepository items;
  @Mock private ClientDriverRepository links;
  @Mock private DependentRepository dependents;
  @Mock private SchoolRepository schools;
  @Mock private AddressRepository addresses;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private MessageSource messages;

  @Captor private ArgumentCaptor<ContractModel> savedContract;

  private ContractService service;
  private ClientDriverModel link;
  private DriverModel carlos;
  private SchoolModel school;
  private CityModel campinas;

  @BeforeEach
  void setUp() {
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));
    when(contracts.save(any())).thenAnswer(call -> call.getArgument(0));
    when(links.save(any())).thenAnswer(call -> call.getArgument(0));
    when(scheduleRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    when(contracts.findActiveByClientDriverId(anyLong())).thenReturn(Optional.empty());
    when(items.findSlotsByDependentIdAndContractStatusIn(anyLong(), any())).thenReturn(List.of());
    when(items.findByContractIdIn(any())).thenReturn(List.of());

    ScheduleService scheduleService =
        new ScheduleService(scheduleRepository, new ScheduleSlotPolicy(), messages);
    service =
        new ContractService(
            contracts,
            items,
            links,
            dependents,
            schools,
            addresses,
            scheduleService,
            new ContractTermsPolicy(),
            new ContractTransitionPolicy(),
            new LinkStatusPolicy(),
            new ContractMapper(new ScheduleMapper()),
            messages);

    campinas = new CityModel();
    campinas.setToken("campinas");
    campinas.setName("Campinas");
    school = new SchoolModel();
    school.setId(7L);
    school.setToken("school");
    school.setName("Escola Municipal");
    when(schools.findById(7L)).thenReturn(Optional.of(school));

    ClientModel maria = new ClientModel();
    maria.setId(1L);
    maria.setToken("maria");
    maria.setUser(user("Maria"));
    carlos = new DriverModel();
    carlos.setId(2L);
    carlos.setToken("carlos");
    carlos.setUser(user("Carlos"));
    carlos.setApprovalStatus(DriverApprovalStatus.APPROVED);
    link = new ClientDriverModel();
    link.setId(3L);
    link.setToken("link");
    link.setClient(maria);
    link.setDriver(carlos);
    link.setStatus(RelationshipStatus.PENDING);
    when(links.findByToken("link")).thenReturn(Optional.of(link));

    registerDependent("lucas", 11L, 1L, 7L, address(21L, "Rua A", "10"));
    registerDependent("ana", 12L, 1L, 7L, address(22L, "Rua B", "20"));
  }

  @Test
  void createsAnActiveContractForTwoSiblingsWithTheirSchoolAndAddressCopied() {
    when(contracts.findStatusesByClientDriverId(3L)).thenReturn(List.of(ContractStatus.ACTIVE));

    ContractResponseDTO response =
        service.create(createRequest(item("lucas", outbound()), item("ana", outbound())));

    verify(contracts).save(savedContract.capture());
    ContractModel contract = savedContract.getValue();
    assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(contract.getClientDriver()).isSameAs(link);
    assertThat(contract.getItems())
        .extracting(
            item -> item.getDependent().getToken(),
            item -> item.getSchool(),
            item -> item.getPickupStreet(),
            item -> item.getPickupNumber(),
            item -> item.getPickupCity())
        .containsExactly(
            tuple("lucas", school, "Rua A", "10", campinas),
            tuple("ana", school, "Rua B", "20", campinas));
    assertThat(response.status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(response.totalAmount()).isEqualByComparingTo("4200.00");
    assertThat(response.items()).hasSize(2);
    assertThat(link.getStatus()).isEqualTo(RelationshipStatus.ACTIVE);
    verify(links).save(link);
  }

  @Test
  void aDependentOfAnotherClientIsRefused() {
    registerDependent("stranger", 13L, 99L, 7L, address(23L, "Rua C", "30"));

    assertRefused(
        () -> service.create(createRequest(item("stranger", outbound()))),
        "422",
        "contract.item.dependent_not_in_link");
    verify(contracts, never()).save(any());
  }

  @Test
  void aDependentWithoutSchoolIsRefused() {
    registerDependent("noschool", 14L, 1L, null, address(24L, "Rua D", "40"));

    assertRefused(
        () -> service.create(createRequest(item("noschool", outbound()))),
        "422",
        "contract.item.school_required");
  }

  @Test
  void aDependentWithoutAddressIsRefused() {
    registerDependent("noaddress", 15L, 1L, 7L, null);

    assertRefused(
        () -> service.create(createRequest(item("noaddress", outbound()))),
        "422",
        "contract.item.address_required");
  }

  @Test
  void theSameDependentTwiceInOneContractIsRefused() {
    assertRefused(
        () -> service.create(createRequest(item("lucas", outbound()), item("lucas", outbound()))),
        "400",
        "contract.item.duplicate_dependent");
  }

  @Test
  void aSlotTheDependentAlreadyHasInAnotherSignedContractNotEndedIsRefused() {
    when(items.findSlotsByDependentIdAndContractStatusIn(11L, ContractStatus.SIGNED_AND_NOT_ENDED))
        .thenReturn(List.of(takenSlot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND)));

    assertRefused(
        () -> service.create(createRequest(item("lucas", outbound()))),
        "409",
        "contract.item.slot_conflict");
    verify(contracts, never()).save(any());
  }

  @Test
  void anOutboundWithOneDriverAndAReturnWithAnotherIsAccepted() {
    when(items.findSlotsByDependentIdAndContractStatusIn(11L, ContractStatus.SIGNED_AND_NOT_ENDED))
        .thenReturn(List.of(takenSlot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND)));
    ScheduleSlotRequestDTO mondayReturn =
        new ScheduleSlotRequestDTO(
            DayOfWeek.MONDAY, RouteLeg.RETURN, OperationShift.AFTERNOON, LocalTime.of(17, 0), null);

    service.create(createRequest(item("lucas", mondayReturn)));

    verify(contracts).save(any());
  }

  @Test
  void aLinkThatAlreadyHasAnActiveContractIsRefused() {
    ContractModel active = contract(ContractStatus.ACTIVE);
    when(contracts.findActiveByClientDriverId(3L)).thenReturn(Optional.of(active));

    assertRefused(
        () -> service.create(createRequest(item("lucas", outbound()))),
        "409",
        "contract.active_conflict");
  }

  @Test
  void aDriverPendingApprovalCannotBeContracted() {
    carlos.setApprovalStatus(DriverApprovalStatus.PENDING);

    assertRefused(
        () -> service.create(createRequest(item("lucas", outbound()))),
        "422",
        "contract.driver.not_approved");
  }

  @Test
  void aBlockedLinkCannotBeContracted() {
    link.setStatus(RelationshipStatus.BLOCKED);

    assertRefused(
        () -> service.create(createRequest(item("lucas", outbound()))),
        "422",
        "contract.link.blocked");
  }

  @Test
  void anInvertedPeriodIsRefusedWithTheTermsPolicyKey() {
    assertRefused(
        () -> service.create(createRequest(ENDS_ON, STARTS_ON, item("lucas", outbound()))),
        "422",
        "contract.period.invalid");
  }

  @Test
  void aPeriodLongerThanTwelveMonthsIsRefusedWithTheTermsPolicyKey() {
    assertRefused(
        () ->
            service.create(
                createRequest(
                    STARTS_ON, STARTS_ON.plusMonths(12).plusDays(1), item("lucas", outbound()))),
        "422",
        "contract.period.too_long");
  }

  @Test
  void endingAnActiveContractDeactivatesTheLink() {
    ContractModel contract = contract(ContractStatus.ACTIVE);
    link.setStatus(RelationshipStatus.ACTIVE);
    when(contracts.findStatusesByClientDriverId(3L)).thenReturn(List.of(ContractStatus.ENDED));

    ContractResponseDTO response = service.update("contract", statusPatch(ContractStatus.ENDED));

    assertThat(contract.getStatus()).isEqualTo(ContractStatus.ENDED);
    assertThat(response.status()).isEqualTo(ContractStatus.ENDED);
    assertThat(link.getStatus()).isEqualTo(RelationshipStatus.INACTIVE);
  }

  @Test
  void suspendingAnActiveContractKeepsTheLinkActive() {
    contract(ContractStatus.ACTIVE);
    link.setStatus(RelationshipStatus.ACTIVE);
    when(contracts.findStatusesByClientDriverId(3L)).thenReturn(List.of(ContractStatus.SUSPENDED));

    service.update("contract", statusPatch(ContractStatus.SUSPENDED));

    assertThat(link.getStatus()).isEqualTo(RelationshipStatus.ACTIVE);
  }

  @Test
  void reopeningAnEndedContractIsRefused() {
    ContractModel contract = contract(ContractStatus.ENDED);

    assertRefused(
        () -> service.update("contract", statusPatch(ContractStatus.ACTIVE)),
        "409",
        "contract.status.invalid_transition");
    assertThat(contract.getStatus()).isEqualTo(ContractStatus.ENDED);
  }

  @Test
  void supersedingByPatchIsRefused() {
    contract(ContractStatus.ACTIVE);

    assertRefused(
        () -> service.update("contract", statusPatch(ContractStatus.SUPERSEDED)),
        "422",
        "contract.status.requires_successor");
  }

  @Test
  void aNullStatusIsRefused() {
    contract(ContractStatus.ACTIVE);

    assertRefused(
        () -> service.update("contract", new ContractUpdateRequestDTO(JsonNullable.of(null))),
        "400",
        "contract.status.required");
  }

  @Test
  void reactivatingASuspendedContractIsRefusedWhenTheLinkHasAnotherActiveOne() {
    contract(ContractStatus.SUSPENDED);
    ContractModel otherActive = contract(ContractStatus.ACTIVE, "other", 31L);
    when(contracts.findActiveByClientDriverId(3L)).thenReturn(Optional.of(otherActive));

    assertRefused(
        () -> service.update("contract", statusPatch(ContractStatus.ACTIVE)),
        "409",
        "contract.active_conflict");
  }

  @Test
  void anEmptyPatchChangesNothing() {
    ContractModel contract = contract(ContractStatus.ACTIVE);

    service.update("contract", new ContractUpdateRequestDTO(null));

    assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    verify(contracts, never()).save(any());
    verify(links, never()).save(any());
  }

  @Test
  void deletingTheOnlyContractReturnsTheLinkToPending() {
    ContractModel contract = contract(ContractStatus.ACTIVE);
    link.setStatus(RelationshipStatus.ACTIVE);
    when(contracts.findStatusesByClientDriverId(3L)).thenReturn(List.of());

    service.delete("contract");

    verify(contracts).delete(contract);
    assertThat(link.getStatus()).isEqualTo(RelationshipStatus.PENDING);
  }

  @Test
  void restoringBringsBackTheItemsAndTheirSchedulesAndRecomputesTheLink() {
    when(contracts.existsDeletedByToken("contract")).thenReturn(true);
    when(contracts.existsActiveConflictForRestore("contract")).thenReturn(false);
    contract(ContractStatus.ACTIVE);
    when(contracts.findStatusesByClientDriverId(3L)).thenReturn(List.of(ContractStatus.ACTIVE));

    service.restore("contract");

    verify(contracts).restoreByToken("contract");
    verify(items).restoreByContractToken("contract");
    verify(items).restoreSchedulesByContractToken("contract");
    verify(items).restoreSlotsByContractToken("contract");
    assertThat(link.getStatus()).isEqualTo(RelationshipStatus.ACTIVE);
  }

  @Test
  void restoringIsRefusedWhenAnItemCollidesWithAnotherSignedContractNotEnded() {
    when(contracts.existsDeletedByToken("contract")).thenReturn(true);
    ContractModel contract = contract(ContractStatus.SUSPENDED);
    ContractItemModel restored = restoredItem(contract);
    when(items.findByContractIdIn(List.of(30L))).thenReturn(List.of(restored));
    when(items.findSlotsByDependentIdAndContractStatusIn(11L, ContractStatus.SIGNED_AND_NOT_ENDED))
        .thenReturn(
            List.of(
                takenSlot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, 40L),
                takenSlot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, 50L)));

    assertRefused(() -> service.restore("contract"), "409", "contract.item.slot_conflict");
  }

  @Test
  void restoringDoesNotCollideWithItsOwnSchedule() {
    when(contracts.existsDeletedByToken("contract")).thenReturn(true);
    ContractModel contract = contract(ContractStatus.ACTIVE);
    ContractItemModel restored = restoredItem(contract);
    when(items.findByContractIdIn(List.of(30L))).thenReturn(List.of(restored));
    when(items.findSlotsByDependentIdAndContractStatusIn(11L, ContractStatus.SIGNED_AND_NOT_ENDED))
        .thenReturn(List.of(takenSlot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, 40L)));
    when(contracts.findStatusesByClientDriverId(3L)).thenReturn(List.of(ContractStatus.ACTIVE));

    ContractResponseDTO response = service.restore("contract");

    assertThat(response.items()).hasSize(1);
  }

  @Test
  void restoringAnEndedContractSkipsTheSlotCheck() {
    when(contracts.existsDeletedByToken("contract")).thenReturn(true);
    ContractModel contract = contract(ContractStatus.ENDED);
    ContractItemModel restored = restoredItem(contract);
    when(items.findByContractIdIn(List.of(30L))).thenReturn(List.of(restored));
    when(contracts.findStatusesByClientDriverId(3L)).thenReturn(List.of(ContractStatus.ENDED));

    service.restore("contract");

    verify(items, never()).findSlotsByDependentIdAndContractStatusIn(anyLong(), any());
  }

  @Test
  void restoringOverAnotherActiveContractOfTheLinkIsRefused() {
    when(contracts.existsDeletedByToken("contract")).thenReturn(true);
    when(contracts.existsActiveConflictForRestore("contract")).thenReturn(true);

    assertRefused(() -> service.restore("contract"), "409", "contract.active_conflict");
    verify(contracts, never()).restoreByToken(anyString());
  }

  @Test
  void restoringAContractThatIsNotDeletedIsNotFound() {
    when(contracts.existsDeletedByToken("contract")).thenReturn(false);

    assertRefused(() -> service.restore("contract"), "404", "contract.not_found");
  }

  @Test
  void anUnknownContractIsNotFound() {
    when(contracts.findByToken("missing")).thenReturn(Optional.empty());

    assertRefused(() -> service.findByToken("missing"), "404", "contract.not_found");
  }

  private void assertRefused(Runnable call, String status, String messageKey) {
    assertThatThrownBy(() -> call.run())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining(status)
        .hasMessageContaining(messageKey);
  }

  private ContractCreateRequestDTO createRequest(ContractItemRequestDTO... contractItems) {
    return createRequest(STARTS_ON, ENDS_ON, contractItems);
  }

  private ContractCreateRequestDTO createRequest(
      LocalDate startsOn, LocalDate endsOn, ContractItemRequestDTO... contractItems) {
    return new ContractCreateRequestDTO(
        "link", startsOn, endsOn, new BigDecimal("4200.00"), 12, 5, List.of(contractItems));
  }

  private ContractItemRequestDTO item(String dependentToken, ScheduleSlotRequestDTO slot) {
    return new ContractItemRequestDTO(dependentToken, new BigDecimal("350.00"), List.of(slot));
  }

  private ScheduleSlotRequestDTO outbound() {
    return new ScheduleSlotRequestDTO(
        DayOfWeek.MONDAY,
        RouteLeg.OUTBOUND,
        OperationShift.MORNING,
        LocalTime.of(6, 40),
        LocalTime.of(6, 50));
  }

  private ContractUpdateRequestDTO statusPatch(ContractStatus status) {
    return new ContractUpdateRequestDTO(JsonNullable.of(status));
  }

  private ContractModel contract(ContractStatus status) {
    return contract(status, "contract", 30L);
  }

  private ContractModel contract(ContractStatus status, String token, Long id) {
    ContractModel contract = new ContractModel();
    contract.setId(id);
    contract.setToken(token);
    contract.setClientDriver(link);
    contract.setStatus(status);
    contract.setStartsOn(STARTS_ON);
    contract.setEndsOn(ENDS_ON);
    when(contracts.findByToken(token)).thenReturn(Optional.of(contract));
    return contract;
  }

  private ScheduleSlotModel takenSlot(DayOfWeek weekday, RouteLeg leg) {
    return takenSlot(weekday, leg, 50L);
  }

  private ScheduleSlotModel takenSlot(DayOfWeek weekday, RouteLeg leg, Long scheduleId) {
    ScheduleModel schedule = new ScheduleModel();
    schedule.setId(scheduleId);
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(weekday);
    slot.setLeg(leg);
    schedule.addSlot(slot);
    return slot;
  }

  private ContractItemModel restoredItem(ContractModel contract) {
    ScheduleSlotModel ownSlot = takenSlot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, 40L);
    ownSlot.setShift(OperationShift.MORNING);
    ownSlot.setWindowStart(LocalTime.of(6, 40));
    ContractItemModel item = new ContractItemModel();
    item.setContract(contract);
    item.setDependent(dependents.findByToken("lucas").orElseThrow());
    item.setSchool(school);
    item.setPickupCity(campinas);
    item.setPickupStreet("Rua A");
    item.setSchedule(ownSlot.getSchedule());
    return item;
  }

  private void registerDependent(
      String token, Long id, Long clientId, Long schoolId, AddressModel address) {
    DependentModel dependent = new DependentModel();
    dependent.setId(id);
    dependent.setToken(token);
    dependent.setName(token);
    dependent.setClientId(clientId);
    dependent.setSchoolId(schoolId);
    if (address != null) {
      dependent.setAddressId(address.getId());
      when(addresses.findById(address.getId())).thenReturn(Optional.of(address));
    }
    when(dependents.findByToken(token)).thenReturn(Optional.of(dependent));
  }

  private AddressModel address(Long id, String street, String number) {
    AddressModel address = new AddressModel();
    address.setId(id);
    address.setCity(campinas);
    address.setStreet(street);
    address.setNumber(number);
    return address;
  }

  private UserModel user(String name) {
    UserModel user = new UserModel();
    user.setName(name);
    return user;
  }
}
