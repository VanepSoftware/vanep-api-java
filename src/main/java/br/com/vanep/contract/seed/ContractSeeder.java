package br.com.vanep.contract.seed;

import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.dto.ContractCreateRequestDTO;
import br.com.vanep.contract.dto.ContractItemRequestDTO;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.contract.service.ContractService;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContractSeeder {

  private static final Logger log = LoggerFactory.getLogger(ContractSeeder.class);
  private static final BigDecimal MONTHLY_AMOUNT = new BigDecimal("350.00");
  private static final int INSTALLMENTS = 12;
  private static final int DUE_DAY = 5;
  private static final LocalTime OUTBOUND_WINDOW_START = LocalTime.of(6, 40);
  private static final LocalTime RETURN_WINDOW_START = LocalTime.of(12, 10);

  private final ContractService contractService;
  private final ContractRepository contracts;
  private final ClientDriverRepository links;
  private final DependentRepository dependents;

  public ContractSeeder(
      ContractService contractService,
      ContractRepository contracts,
      ClientDriverRepository links,
      DependentRepository dependents) {
    this.contractService = contractService;
    this.contracts = contracts;
    this.links = links;
    this.dependents = dependents;
  }

  @Transactional
  public void seed() {
    ClientDriverModel link = links.findPage(PageRequest.of(0, 1)).stream().findFirst().orElse(null);
    if (link == null || !contracts.findByClientDriverId(link.getId()).isEmpty()) {
      return;
    }
    DependentModel dependent =
        dependents.findByClientId(link.getClient().getId()).stream().findFirst().orElse(null);
    if (dependent == null) {
      log.info("Seed: contract seed skipped; the seeded link's client has no dependent.");
      return;
    }
    if (dependent.getSchoolId() == null || dependent.getAddressId() == null) {
      log.info("Seed: contract seed skipped; the dependent has no school or address.");
      return;
    }

    contractService.create(createRequest(link, dependent));
    log.info("Seed: active contract created for link {}.", link.getToken());
  }

  ContractCreateRequestDTO createRequest(ClientDriverModel link, DependentModel dependent) {
    LocalDate startsOn = LocalDate.now().withDayOfMonth(1);
    ContractItemRequestDTO item =
        new ContractItemRequestDTO(dependent.getToken(), MONTHLY_AMOUNT, weekdayRoundTrip());
    return new ContractCreateRequestDTO(
        link.getToken(),
        startsOn,
        startsOn.plusMonths(INSTALLMENTS - 1),
        MONTHLY_AMOUNT.multiply(BigDecimal.valueOf(INSTALLMENTS)),
        INSTALLMENTS,
        DUE_DAY,
        List.of(item));
  }

  List<ScheduleSlotRequestDTO> weekdayRoundTrip() {
    return EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY).stream()
        .flatMap(
            weekday ->
                Stream.of(
                    new ScheduleSlotRequestDTO(
                        weekday,
                        RouteLeg.OUTBOUND,
                        OperationShift.MORNING,
                        OUTBOUND_WINDOW_START,
                        null),
                    new ScheduleSlotRequestDTO(
                        weekday,
                        RouteLeg.RETURN,
                        OperationShift.AFTERNOON,
                        RETURN_WINDOW_START,
                        null)))
        .toList();
  }
}
