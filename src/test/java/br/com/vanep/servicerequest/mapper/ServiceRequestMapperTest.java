package br.com.vanep.servicerequest.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.servicerequest.dto.ServiceRequestClientResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDriverResponseDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ServiceRequestMapperTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
  private static final LocalDate TEN_YEARS_AGO_TODAY = LocalDate.of(2016, 10, 21);
  private static final LocalDate TEN_YEARS_AGO_TOMORROW = LocalDate.of(2016, 10, 22);

  private final ServiceRequestMapper mapper = new ServiceRequestMapper();
  private ServiceRequestModel request;

  @BeforeEach
  void setUp() {
    UserModel maria = new UserModel();
    maria.setName("Maria Souza Lima");
    maria.setEmail("maria.souza@vanep.com");
    maria.setPhone("11999990000");
    maria.setDocument("39053344705");
    ClientModel client = new ClientModel();
    client.setUser(maria);
    UserModel carlos = new UserModel();
    carlos.setName("Carlos Pereira");
    DriverModel driver = new DriverModel();
    driver.setRating(new BigDecimal("4.70"));
    driver.setToken("carlos");
    driver.setUser(carlos);
    ClientDriverModel link = new ClientDriverModel();
    link.setToken("link");
    link.setClient(client);
    link.setDriver(driver);

    request = new ServiceRequestModel();
    request.setToken("request");
    request.setClientDriver(link);
    request.setStatus(ServiceRequestStatus.OPEN);
    request.setDesiredStartDate(LocalDate.of(2027, 2, 1));
    request.setNotes("Portão azul");
    request.setExpiresAt(Instant.parse("2026-10-24T12:00:00Z"));
    request.addItem(
        item(
            12L,
            22L,
            "Ana Souza Lima",
            TEN_YEARS_AGO_TOMORROW,
            day(DayOfWeek.MONDAY, SchoolShift.AFTERNOON)));
    request.addItem(
        item(
            11L,
            21L,
            "Lucas Souza Lima",
            TEN_YEARS_AGO_TODAY,
            day(DayOfWeek.FRIDAY, SchoolShift.MORNING),
            day(DayOfWeek.MONDAY, SchoolShift.FULLTIME)));
  }

  @Test
  void theDriverSeesFirstNamesAndAgesInWholeYears() {
    ServiceRequestDriverResponseDTO view =
        mapper.toDriverResponse(
            request, Map.of(21L, "lucas", 22L, "ana"), TODAY, null, "conversation");

    assertThat(view.clientFirstName()).isEqualTo("Maria");
    assertThat(view.conversationToken()).isEqualTo("conversation");
    assertThat(view.items())
        .extracting(
            item -> item.dependentToken(),
            item -> item.dependentFirstName(),
            item -> item.dependentAge(),
            item -> item.schoolName(),
            item -> item.pickupCityName(),
            item -> item.pickupStateUf(),
            item -> item.pickupNeighborhood())
        .containsExactly(
            tuple("lucas", "Lucas", 10, "Escola Municipal", "Campinas", "SP", "Cambuí"),
            tuple("ana", "Ana", 9, "Escola Municipal", "Campinas", "SP", "Cambuí"));
  }

  @Test
  void theDriverViewCarriesNoSurnameBirthDateOrContactOfTheClient() {
    String json =
        JsonMapper.builder()
            .build()
            .writeValueAsString(mapper.toDriverResponse(request, Map.of(), TODAY, null, null));

    assertThat(json)
        .doesNotContain("Souza", "Lima", "2016")
        .doesNotContain("maria.souza@vanep.com", "11999990000", "39053344705")
        .doesNotContain("Street", "street", "zip", "googlePlace", "birth");
  }

  @Test
  void theClientSeesTheFullNameAndBirthDateAsSent() {
    ServiceRequestClientResponseDTO view =
        mapper.toClientResponse(request, Map.of(21L, "lucas", 22L, "ana"), null, "conversation");

    assertThat(view.driverToken()).isEqualTo("carlos");
    assertThat(view.conversationToken()).isEqualTo("conversation");
    assertThat(view.driverName()).isEqualTo("Carlos Pereira");
    assertThat(view.driverRating()).isEqualByComparingTo("4.70");
    assertThat(view.items())
        .extracting(
            item -> item.dependentToken(),
            item -> item.dependentName(),
            item -> item.dependentBirthDate())
        .containsExactly(
            tuple("lucas", "Lucas Souza Lima", TEN_YEARS_AGO_TODAY),
            tuple("ana", "Ana Souza Lima", TEN_YEARS_AGO_TOMORROW));
  }

  @Test
  void theDaysComeMondayFirst() {
    ServiceRequestClientResponseDTO view = mapper.toClientResponse(request, Map.of(), null, null);

    assertThat(view.items().getFirst().days())
        .extracting(day -> day.weekday(), day -> day.schoolShift())
        .containsExactly(
            tuple(DayOfWeek.MONDAY, SchoolShift.FULLTIME),
            tuple(DayOfWeek.FRIDAY, SchoolShift.MORNING));
  }

  @Test
  void theItemCopiesTheDependentSchoolAndPickupAndKeepsTheIds() {
    ServiceRequestItemModel item = mapper.toItemModel(dependent(), school(), pickup("Cambuí"));

    assertThat(item.getDependentId()).isEqualTo(21L);
    assertThat(item.getSchoolId()).isEqualTo(7L);
    assertThat(item.getPickupCityId()).isEqualTo(30L);
    assertThat(item.getSnapshot())
        .isEqualTo(
            new ServiceRequestItemModel.Snapshot(
                ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
                new ServiceRequestItemModel.Snapshot.Dependent(
                    "Lucas Souza Lima", TEN_YEARS_AGO_TODAY),
                new ServiceRequestItemModel.Snapshot.School("Escola Municipal"),
                new ServiceRequestItemModel.Snapshot.Pickup("Campinas", "SP", "Cambuí")));
  }

  @Test
  void anAddressWithoutNeighborhoodLeavesTheNeighborhoodEmpty() {
    ServiceRequestItemModel item = mapper.toItemModel(dependent(), school(), pickup(null));

    assertThat(item.getSnapshot().pickup().neighborhood()).isNull();
  }

  private ServiceRequestItemModel item(
      Long id, Long dependentId, String name, LocalDate birthDate, ServiceRequestDayModel... days) {
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setId(id);
    item.setDependentId(dependentId);
    item.setSnapshot(
        new ServiceRequestItemModel.Snapshot(
            ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
            new ServiceRequestItemModel.Snapshot.Dependent(name, birthDate),
            new ServiceRequestItemModel.Snapshot.School("Escola Municipal"),
            new ServiceRequestItemModel.Snapshot.Pickup("Campinas", "SP", "Cambuí")));
    for (ServiceRequestDayModel day : days) {
      item.addDay(day);
    }
    return item;
  }

  private ServiceRequestDayModel day(DayOfWeek weekday, SchoolShift schoolShift) {
    ServiceRequestDayModel day = new ServiceRequestDayModel();
    day.setWeekday(weekday);
    day.setSchoolShift(schoolShift);
    day.setNeedsOutbound(true);
    day.setNeedsReturn(true);
    return day;
  }

  private DependentModel dependent() {
    DependentModel dependent = new DependentModel();
    dependent.setId(21L);
    dependent.setName("Lucas Souza Lima");
    dependent.setBirthDate(TEN_YEARS_AGO_TODAY);
    return dependent;
  }

  private SchoolModel school() {
    SchoolModel school = new SchoolModel();
    school.setId(7L);
    school.setName("Escola Municipal");
    return school;
  }

  private AddressModel pickup(String neighborhood) {
    StateModel state = new StateModel();
    state.setUf("SP");
    CityModel city = new CityModel();
    city.setId(30L);
    city.setName("Campinas");
    city.setState(state);
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setStreet("Rua A");
    address.setNeighborhood(neighborhood);
    return address;
  }
}
