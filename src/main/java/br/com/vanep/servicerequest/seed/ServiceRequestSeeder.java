package br.com.vanep.servicerequest.seed;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contractreadiness.service.ContractReadinessService;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.servicerequest.dto.ServiceRequestCreateRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDayRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestItemRequestDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.repository.ServiceRequestRepository;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ServiceRequestSeeder {

  private static final Logger log = LoggerFactory.getLogger(ServiceRequestSeeder.class);
  private static final String SEED_CLIENT_EMAIL = "ana.souza@seed.vanep.com.br";

  private final ServiceRequestService serviceRequestService;
  private final ServiceRequestRepository requests;
  private final UserRepository users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final ClientDriverRepository links;
  private final DependentRepository dependents;
  private final DependentSchoolDayRepository schoolDays;
  private final ContractReadinessService readiness;

  public ServiceRequestSeeder(
      ServiceRequestService serviceRequestService,
      ServiceRequestRepository requests,
      UserRepository users,
      ClientRepository clients,
      DriverRepository drivers,
      ClientDriverRepository links,
      DependentRepository dependents,
      DependentSchoolDayRepository schoolDays,
      ContractReadinessService readiness) {
    this.serviceRequestService = serviceRequestService;
    this.requests = requests;
    this.users = users;
    this.clients = clients;
    this.drivers = drivers;
    this.links = links;
    this.dependents = dependents;
    this.schoolDays = schoolDays;
    this.readiness = readiness;
  }

  @Transactional
  public void seed() {
    UserModel user = users.findByEmail(SEED_CLIENT_EMAIL).orElse(null);
    if (user == null) {
      return;
    }
    ClientModel client = clients.findByUserId(user.getId()).orElse(null);
    if (client == null || hasAnyRequest(client)) {
      return;
    }
    DriverModel driver = findDriverNotLinkedTo(client);
    DependentModel dependent =
        dependents.findByClientId(client.getId()).stream()
            .filter(candidate -> candidate.isDefaultDependent())
            .findFirst()
            .orElse(null);
    if (driver == null || dependent == null) {
      log.info("Seed: service request skipped; no unlinked driver or no default dependent.");
      return;
    }
    List<DependentSchoolDayModel> grid = schoolDays.findByDependentId(dependent.getId());
    if (!readiness.isReady(user, List.of(dependent), grid)) {
      log.info("Seed: service request skipped; the seed client or dependent is not ready.");
      return;
    }

    serviceRequestService.send(user.getToken(), createRequest(driver, dependent, grid));
    log.info("Seed: open service request created for {}.", SEED_CLIENT_EMAIL);
  }

  boolean hasAnyRequest(ClientModel client) {
    return requests.countByClientIdAndStatusIn(
            client.getId(), EnumSet.allOf(ServiceRequestStatus.class))
        > 0;
  }

  DriverModel findDriverNotLinkedTo(ClientModel client) {
    return drivers.findAll().stream()
        .filter(driver -> driver.getApprovalStatus() == DriverApprovalStatus.APPROVED)
        .filter(driver -> driver.isActive() && driver.isAvailable())
        .filter(driver -> links.findByPair(client.getId(), driver.getId()).isEmpty())
        .min(Comparator.comparing(driver -> driver.getId()))
        .orElse(null);
  }

  ServiceRequestCreateRequestDTO createRequest(
      DriverModel driver, DependentModel dependent, List<DependentSchoolDayModel> grid) {
    List<ServiceRequestDayRequestDTO> roundTripOnSchoolDays =
        grid.stream()
            .map(day -> day.getWeekday())
            .sorted()
            .map(weekday -> new ServiceRequestDayRequestDTO(weekday, true, true))
            .toList();
    return new ServiceRequestCreateRequestDTO(
        driver.getToken(),
        LocalDate.now().plusMonths(1).withDayOfMonth(1),
        "Portão azul, tocar o interfone.",
        List.of(new ServiceRequestItemRequestDTO(dependent.getToken(), roundTripOnSchoolDays)));
  }
}
