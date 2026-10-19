package br.com.vanep.dependent.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DependentSchoolDayRepositoryTest {

  @Autowired private DependentSchoolDayRepository schoolDays;
  @Autowired private DependentRepository dependents;
  @Autowired private ClientRepository clients;
  @Autowired private UserRepository users;

  private DependentModel lucas;

  @BeforeEach
  void setUp() {
    lucas = createDependent("Lucas");
  }

  @Test
  void storesAndRereadsTheGridOfADependent() {
    schoolDays.save(newDay(lucas, DayOfWeek.TUESDAY, SchoolShift.FULLTIME));
    schoolDays.save(newDay(lucas, DayOfWeek.MONDAY, SchoolShift.MORNING));

    assertThat(schoolDays.findByDependentId(lucas.getId()))
        .extracting(day -> day.getWeekday(), day -> day.getSchoolShift())
        .containsExactlyInAnyOrder(
            tuple(DayOfWeek.MONDAY, SchoolShift.MORNING),
            tuple(DayOfWeek.TUESDAY, SchoolShift.FULLTIME));
  }

  @Test
  void leavesTheRemovedDaysOut() {
    DependentSchoolDayModel monday =
        schoolDays.save(newDay(lucas, DayOfWeek.MONDAY, SchoolShift.MORNING));
    schoolDays.save(newDay(lucas, DayOfWeek.FRIDAY, SchoolShift.MORNING));

    schoolDays.delete(monday);

    assertThat(schoolDays.findByDependentId(lucas.getId()))
        .extracting(day -> day.getWeekday())
        .containsExactly(DayOfWeek.FRIDAY);
  }

  @Test
  void readsOnlyTheGridOfTheGivenDependent() {
    DependentModel marina = createDependent("Marina");
    schoolDays.save(newDay(lucas, DayOfWeek.MONDAY, SchoolShift.MORNING));
    schoolDays.save(newDay(marina, DayOfWeek.MONDAY, SchoolShift.AFTERNOON));

    assertThat(schoolDays.findByDependentId(marina.getId()))
        .extracting(day -> day.getSchoolShift())
        .containsExactly(SchoolShift.AFTERNOON);
  }

  private DependentSchoolDayModel newDay(
      DependentModel dependent, DayOfWeek weekday, SchoolShift schoolShift) {
    DependentSchoolDayModel day = new DependentSchoolDayModel();
    day.setDependentId(dependent.getId());
    day.setWeekday(weekday);
    day.setSchoolShift(schoolShift);
    return day;
  }

  private DependentModel createDependent(String name) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(client().getId());
    dependent.setName(name);
    return dependents.save(dependent);
  }

  private ClientModel client() {
    return clients.findAll().stream().findFirst().orElseGet(() -> createClient());
  }

  private ClientModel createClient() {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Ana");
    user.setEmail("ana@vanep.com");
    user.setDocument("11144477735");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    ClientModel client = new ClientModel();
    client.setUser(users.save(user));
    return clients.save(client);
  }
}
