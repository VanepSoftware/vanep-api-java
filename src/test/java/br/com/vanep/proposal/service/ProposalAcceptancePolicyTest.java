package br.com.vanep.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.enums.ProposalAcceptanceChange;
import br.com.vanep.proposal.model.ProposalModel;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.vehicle.model.VehicleModel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProposalAcceptancePolicyTest {

  private final ProposalAcceptancePolicy policy = new ProposalAcceptancePolicy();

  private ServiceRequestModel request;
  private ProposalModel proposal;
  private DependentModel lucas;
  private AddressModel lucasHome;
  private List<DependentSchoolDayModel> grid;
  private DriverModel carlos;
  private VehicleModel van;

  @BeforeEach
  void setUp() {
    request = new ServiceRequestModel();
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setDependentId(21L);
    item.setSchoolId(7L);
    item.setPickupCityId(5L);
    item.setSnapshot(
        new ServiceRequestItemModel.Snapshot(
            ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
            new ServiceRequestItemModel.Snapshot.Dependent("Lucas Souza", LocalDate.of(2016, 4, 2)),
            new ServiceRequestItemModel.Snapshot.School("Escola Municipal"),
            new ServiceRequestItemModel.Snapshot.Pickup("Campinas", "SP", "Cambuí")));
    item.addDay(day(DayOfWeek.MONDAY, SchoolShift.MORNING));
    item.addDay(day(DayOfWeek.THURSDAY, SchoolShift.FULLTIME));
    request.addItem(item);

    lucas = new DependentModel();
    lucas.setId(21L);
    lucas.setName("Lucas Souza");
    lucas.setSchoolId(7L);
    lucas.setAddressId(31L);
    CityModel campinas = new CityModel();
    campinas.setId(5L);
    lucasHome = new AddressModel();
    lucasHome.setId(31L);
    lucasHome.setCity(campinas);
    lucasHome.setNeighborhood("Cambuí");
    grid = new ArrayList<>();
    grid.add(gridDay(DayOfWeek.MONDAY, SchoolShift.MORNING));
    grid.add(gridDay(DayOfWeek.THURSDAY, SchoolShift.FULLTIME));
    grid.add(gridDay(DayOfWeek.FRIDAY, SchoolShift.MORNING));

    carlos = new DriverModel();
    carlos.setId(20L);
    carlos.setApprovalStatus(DriverApprovalStatus.APPROVED);
    van = new VehicleModel();
    van.setId(60L);
    van.setDriver(carlos);
    van.setPlate("VAN1A23");
    van.setCapacity(15);
    proposal = new ProposalModel();
    proposal.setVehicleId(60L);
    proposal.setSnapshot(
        new ProposalModel.Snapshot(
            ProposalModel.Snapshot.CURRENT_VERSION,
            new ProposalModel.Snapshot.Vehicle(
                "VAN1A23", "Renault", "Master", 2022, "Branca", 15)));
  }

  @Test
  void nothingChanged() {
    assertThat(findChanges()).isEmpty();
  }

  @Test
  void aCorrectedNameOrBirthDateDoesNotCount() {
    lucas.setName("Lucas de Souza");
    lucas.setBirthDate(LocalDate.of(2016, 4, 3));

    assertThat(findChanges()).isEmpty();
  }

  @Test
  void aRemovedDependent() {
    assertThat(policy.findChanges(request, proposal, List.of(), Map.of(), grid, van, carlos))
        .containsExactly(ProposalAcceptanceChange.DEPENDENT_REMOVED);
  }

  @Test
  void anotherSchool() {
    lucas.setSchoolId(8L);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.SCHOOL_CHANGED);
  }

  @Test
  void anotherPickupCity() {
    CityModel valinhos = new CityModel();
    valinhos.setId(6L);
    lucasHome.setCity(valinhos);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.PICKUP_CHANGED);
  }

  @Test
  void anotherPickupNeighborhood() {
    lucasHome.setNeighborhood("Taquaral");

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.PICKUP_CHANGED);
  }

  @Test
  void theSameNeighborhoodWrittenDifferentlyDoesNotCount() {
    lucasHome.setNeighborhood(" cambuí ");

    assertThat(findChanges()).isEmpty();
  }

  @Test
  void aPickupAddressThatIsGone() {
    assertThat(policy.findChanges(request, proposal, List.of(lucas), Map.of(), grid, van, carlos))
        .containsExactly(ProposalAcceptanceChange.PICKUP_CHANGED);
  }

  @Test
  void aRequestedWeekdayLeftTheGrid() {
    grid.removeIf(day -> day.getWeekday() == DayOfWeek.MONDAY);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.SCHOOL_DAY_CHANGED);
  }

  @Test
  void aRequestedWeekdayHasAnotherPeriod() {
    grid.getFirst().setSchoolShift(SchoolShift.AFTERNOON);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.SCHOOL_DAY_CHANGED);
  }

  @Test
  void aWeekdayThatWasNotRequestedDoesNotCount() {
    grid.getLast().setSchoolShift(SchoolShift.FULLTIME);

    assertThat(findChanges()).isEmpty();
  }

  @Test
  void aRemovedVehicle() {
    assertThat(
            policy.findChanges(request, proposal, List.of(lucas), addresses(), grid, null, carlos))
        .containsExactly(ProposalAcceptanceChange.VEHICLE_CHANGED);
  }

  @Test
  void anInactiveVehicle() {
    van.setActive(false);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.VEHICLE_CHANGED);
  }

  @Test
  void aVehicleOfAnotherDriver() {
    DriverModel joana = new DriverModel();
    joana.setId(23L);
    van.setDriver(joana);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.VEHICLE_CHANGED);
  }

  @Test
  void anotherPlate() {
    van.setPlate("VAN9Z99");

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.VEHICLE_CHANGED);
  }

  @Test
  void anotherCapacity() {
    van.setCapacity(12);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.VEHICLE_CHANGED);
  }

  @Test
  void anotherColorDoesNotCount() {
    van.setColor("Prata");

    assertThat(findChanges()).isEmpty();
  }

  @Test
  void anInactiveDriver() {
    carlos.setActive(false);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.DRIVER_UNAVAILABLE);
  }

  @Test
  void aDriverNoLongerApproved() {
    carlos.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);

    assertThat(findChanges()).containsExactly(ProposalAcceptanceChange.DRIVER_UNAVAILABLE);
  }

  @Test
  void listsEveryChangeOnce() {
    lucasHome.setNeighborhood("Taquaral");
    grid.clear();
    van.setPlate("VAN9Z99");

    assertThat(findChanges())
        .containsExactly(
            ProposalAcceptanceChange.PICKUP_CHANGED,
            ProposalAcceptanceChange.SCHOOL_DAY_CHANGED,
            ProposalAcceptanceChange.VEHICLE_CHANGED);
  }

  private List<ProposalAcceptanceChange> findChanges() {
    return List.copyOf(
        policy.findChanges(request, proposal, List.of(lucas), addresses(), grid, van, carlos));
  }

  private Map<Long, AddressModel> addresses() {
    return Map.of(31L, lucasHome);
  }

  private ServiceRequestDayModel day(DayOfWeek weekday, SchoolShift schoolShift) {
    ServiceRequestDayModel day = new ServiceRequestDayModel();
    day.setWeekday(weekday);
    day.setSchoolShift(schoolShift);
    day.setNeedsOutbound(true);
    day.setNeedsReturn(true);
    return day;
  }

  private DependentSchoolDayModel gridDay(DayOfWeek weekday, SchoolShift schoolShift) {
    DependentSchoolDayModel day = new DependentSchoolDayModel();
    day.setDependentId(21L);
    day.setWeekday(weekday);
    day.setSchoolShift(schoolShift);
    return day;
  }
}
