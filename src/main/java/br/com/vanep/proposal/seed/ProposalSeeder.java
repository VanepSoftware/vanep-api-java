package br.com.vanep.proposal.seed;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.dto.ProposalCreateRequestDTO;
import br.com.vanep.proposal.dto.ProposalItemRequestDTO;
import br.com.vanep.proposal.enums.ProposalAdjustmentPolicy;
import br.com.vanep.proposal.enums.ProposalCancellationPolicy;
import br.com.vanep.proposal.repository.ProposalRepository;
import br.com.vanep.proposal.service.ProposalService;
import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.servicerequest.repository.ServiceRequestRepository;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProposalSeeder {

  private static final Logger log = LoggerFactory.getLogger(ProposalSeeder.class);
  private static final String SEED_CLIENT_EMAIL = "ana.souza@seed.vanep.com.br";
  private static final LocalTime OUTBOUND_WINDOW_START = LocalTime.of(6, 40);
  private static final LocalTime RETURN_WINDOW_START = LocalTime.of(12, 10);
  private static final LocalTime FULLTIME_RETURN_WINDOW_START = LocalTime.of(17, 20);
  private static final BigDecimal MONTHLY_AMOUNT = new BigDecimal("420.00");
  private static final int INSTALLMENTS = 10;

  private final ProposalService proposalService;
  private final ProposalRepository proposals;
  private final ServiceRequestRepository requests;
  private final UserRepository users;
  private final ClientRepository clients;
  private final DependentRepository dependents;
  private final VehicleRepository vehicles;

  public ProposalSeeder(
      ProposalService proposalService,
      ProposalRepository proposals,
      ServiceRequestRepository requests,
      UserRepository users,
      ClientRepository clients,
      DependentRepository dependents,
      VehicleRepository vehicles) {
    this.proposalService = proposalService;
    this.proposals = proposals;
    this.requests = requests;
    this.users = users;
    this.clients = clients;
    this.dependents = dependents;
    this.vehicles = vehicles;
  }

  @Transactional
  public void seed() {
    ServiceRequestModel request = findOpenSeedRequest();
    if (request == null
        || proposals.findTopByServiceRequestIdOrderByVersionDesc(request.getId()).isPresent()) {
      return;
    }
    DriverModel driver = request.getClientDriver().getDriver();
    VehicleModel vehicle =
        vehicles.findActiveWithPhotosByDriverId(driver.getId()).stream().findFirst().orElse(null);
    if (vehicle == null) {
      log.info("Seed: proposal skipped; the driver of the seeded request has no active vehicle.");
      return;
    }

    proposalService.send(
        driver.getUser().getToken(), request.getToken(), createProposal(request, vehicle));
    log.info("Seed: SENT proposal created for the seeded service request.");
  }

  ServiceRequestModel findOpenSeedRequest() {
    UserModel user = users.findByEmail(SEED_CLIENT_EMAIL).orElse(null);
    ClientModel client = user == null ? null : clients.findByUserId(user.getId()).orElse(null);
    if (client == null) {
      return null;
    }
    return requests
        .findPageByClientIdAndStatusIn(
            client.getId(), Set.of(ServiceRequestStatus.OPEN), PageRequest.of(0, 1))
        .stream()
        .filter(request -> request.getExpiresAt().isAfter(Instant.now()))
        .findFirst()
        .flatMap(
            request -> requests.findWithItemsByIdIn(List.of(request.getId())).stream().findFirst())
        .orElse(null);
  }

  ProposalCreateRequestDTO createProposal(ServiceRequestModel request, VehicleModel vehicle) {
    List<ServiceRequestItemModel> items =
        request.getItems().stream().sorted(Comparator.comparing(item -> item.getId())).toList();
    Map<Long, String> tokens =
        dependents
            .findTokensByIdIn(items.stream().map(item -> item.getDependentId()).toList())
            .stream()
            .collect(Collectors.toMap(row -> row.getId(), row -> row.getToken()));
    List<ProposalItemRequestDTO> itemRequests =
        items.stream()
            .map(
                item ->
                    new ProposalItemRequestDTO(
                        tokens.get(item.getDependentId()), MONTHLY_AMOUNT, slotsFor(item)))
            .toList();
    BigDecimal monthly = MONTHLY_AMOUNT.multiply(BigDecimal.valueOf(items.size()));
    return new ProposalCreateRequestDTO(
        request.getDesiredStartDate(),
        request.getDesiredStartDate().plusMonths(INSTALLMENTS).minusDays(1),
        monthly.multiply(BigDecimal.valueOf(INSTALLMENTS)),
        INSTALLMENTS,
        5,
        new BigDecimal("100.00"),
        ProposalAdjustmentPolicy.NONE,
        ProposalCancellationPolicy.NOTICE_30_DAYS,
        vehicle.getToken(),
        null,
        "Saio da garagem às 6h20; aviso pelo chat se atrasar.",
        itemRequests);
  }

  List<ScheduleSlotRequestDTO> slotsFor(ServiceRequestItemModel item) {
    List<ScheduleSlotRequestDTO> slots = new ArrayList<>();
    item.getDays().stream()
        .sorted(Comparator.comparing(day -> day.getWeekday()))
        .forEach(
            day -> {
              if (day.isNeedsOutbound()) {
                slots.add(
                    new ScheduleSlotRequestDTO(
                        day.getWeekday(),
                        RouteLeg.OUTBOUND,
                        OperationShift.MORNING,
                        OUTBOUND_WINDOW_START,
                        null));
              }
              if (day.isNeedsReturn()) {
                slots.add(
                    new ScheduleSlotRequestDTO(
                        day.getWeekday(),
                        RouteLeg.RETURN,
                        OperationShift.AFTERNOON,
                        returnWindowStartOf(day),
                        null));
              }
            });
    return slots;
  }

  LocalTime returnWindowStartOf(ServiceRequestDayModel day) {
    return day.getSchoolShift() == SchoolShift.FULLTIME
        ? FULLTIME_RETURN_WINDOW_START
        : RETURN_WINDOW_START;
  }
}
