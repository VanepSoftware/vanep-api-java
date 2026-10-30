package br.com.vanep.dependent.seed;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.dto.DependentSchoolDayRequestDTO;
import br.com.vanep.dependent.mapper.DependentMapper;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.school.seed.SchoolSeeder;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DependentSeeder {

  private static final Logger log = LoggerFactory.getLogger(DependentSeeder.class);
  private static final String SEED_CLIENT_EMAIL = "ana.souza@seed.vanep.com.br";
  private static final Map<DayOfWeek, SchoolShift> FULL_TIME_ON_TUESDAY_AND_THURSDAY =
      Map.of(
          DayOfWeek.MONDAY, SchoolShift.MORNING,
          DayOfWeek.TUESDAY, SchoolShift.FULLTIME,
          DayOfWeek.WEDNESDAY, SchoolShift.MORNING,
          DayOfWeek.THURSDAY, SchoolShift.FULLTIME,
          DayOfWeek.FRIDAY, SchoolShift.MORNING);
  private static final Map<DayOfWeek, SchoolShift> MORNINGS_ON_WEEKDAYS =
      Map.of(
          DayOfWeek.MONDAY, SchoolShift.MORNING,
          DayOfWeek.TUESDAY, SchoolShift.MORNING,
          DayOfWeek.WEDNESDAY, SchoolShift.MORNING,
          DayOfWeek.THURSDAY, SchoolShift.MORNING,
          DayOfWeek.FRIDAY, SchoolShift.MORNING);
  private static final List<DependentSeed> SEEDS =
      List.of(
          new DependentSeed(
              "Lucas Souza",
              "98765432100",
              LocalDate.of(2016, 4, 2),
              Gender.MALE,
              true,
              FULL_TIME_ON_TUESDAY_AND_THURSDAY),
          new DependentSeed(
              "Marina Souza",
              "45317828791",
              LocalDate.of(2019, 9, 15),
              Gender.FEMALE,
              false,
              MORNINGS_ON_WEEKDAYS));

  private final DependentRepository dependents;
  private final DependentSchoolDayRepository schoolDays;
  private final DependentMapper mapper;
  private final ClientRepository clients;
  private final UserRepository users;
  private final SchoolRepository schools;
  private final AddressRepository addresses;

  public DependentSeeder(
      DependentRepository dependents,
      DependentSchoolDayRepository schoolDays,
      DependentMapper mapper,
      ClientRepository clients,
      UserRepository users,
      SchoolRepository schools,
      AddressRepository addresses) {
    this.dependents = dependents;
    this.schoolDays = schoolDays;
    this.mapper = mapper;
    this.clients = clients;
    this.users = users;
    this.schools = schools;
    this.addresses = addresses;
  }

  public void seed() {
    Optional<Long> clientId = resolveSeedClientId();
    if (clientId.isEmpty()) {
      log.info("Seed: dependent seed skipped; seed client not found ({}).", SEED_CLIENT_EMAIL);
      return;
    }
    SEEDS.forEach(seed -> createIfMissing(clientId.get(), seed));
  }

  private Optional<Long> resolveSeedClientId() {
    return users
        .findByEmail(SEED_CLIENT_EMAIL)
        .flatMap(user -> clients.findByUserId(user.getId()))
        .map(client -> client.getId());
  }

  private void createIfMissing(Long clientId, DependentSeed seed) {
    if (dependents.existsByClientIdAndName(clientId, seed.name())
        || dependents.existsByDocument(seed.document())) {
      return;
    }
    DependentModel dependent = new DependentModel();
    dependent.setClientId(clientId);
    dependent.setName(seed.name());
    dependent.setDocument(seed.document());
    dependent.setBirthDate(seed.birthDate());
    dependent.setGender(seed.gender());
    dependent.setDefaultDependent(seed.isDefault());
    schools
        .findFirstByName(SchoolSeeder.SEED_SCHOOL_NAME)
        .ifPresentOrElse(
            school -> {
              dependent.setSchoolId(school.getId());
              dependent.setAddressId(createHome(school).getId());
            },
            () ->
                log.info(
                    "Seed: {} created without school and address; no seed school.", seed.name()));
    DependentModel saved = dependents.save(dependent);
    seed.grid()
        .forEach(
            (weekday, schoolShift) ->
                schoolDays.save(
                    mapper.toSchoolDayModel(
                        saved.getId(), new DependentSchoolDayRequestDTO(weekday, schoolShift))));
    log.info("Seed: dependent created ({}).", seed.name());
  }

  private AddressModel createHome(SchoolModel school) {
    AddressModel address = new AddressModel();
    address.setCity(school.getCity());
    address.setZipCode("01001000");
    address.setStreet("Rua das Acácias");
    address.setNumber("250");
    address.setNeighborhood("Centro");
    return addresses.save(address);
  }

  private record DependentSeed(
      String name,
      String document,
      LocalDate birthDate,
      Gender gender,
      boolean isDefault,
      Map<DayOfWeek, SchoolShift> grid) {}
}
