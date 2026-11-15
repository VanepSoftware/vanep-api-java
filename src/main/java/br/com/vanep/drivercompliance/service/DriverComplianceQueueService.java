package br.com.vanep.drivercompliance.service;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceCountsResponseDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceRowResponseDTO;
import br.com.vanep.drivercompliance.enums.DriverComplianceView;
import br.com.vanep.drivercompliance.enums.DriverComplianceView.EvaluatedDriver;
import br.com.vanep.drivercompliance.enums.NextAction;
import br.com.vanep.drivercompliance.mapper.DriverComplianceMapper;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicle.service.VehicleService;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepted decision: every request evaluates the candidate drivers with DriverCompliancePolicy and
 * pages in memory, so the rule lives in one place and no table has to follow every write. The cost
 * grows with the number of drivers; storing the evaluation per driver changes only this class.
 */
@Service
public class DriverComplianceQueueService {

  private final DriverRepository drivers;
  private final VehicleRepository vehicles;
  private final DriverComplianceService complianceService;
  private final DriverComplianceMapper mapper;

  public DriverComplianceQueueService(
      DriverRepository drivers,
      VehicleRepository vehicles,
      DriverComplianceService complianceService,
      DriverComplianceMapper mapper) {
    this.drivers = drivers;
    this.vehicles = vehicles;
    this.complianceService = complianceService;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public Page<DriverComplianceRowResponseDTO> findQueue(
      DriverComplianceView view, String search, Pageable pageable) {
    LocalDate today = SchoolTransportCredentialService.today();
    List<EvaluatedDriver> matching =
        filter(evaluate(candidates(view, search), today), view, today)
            .sorted(view.comparator())
            .toList();
    List<EvaluatedDriver> page =
        pageable.isUnpaged()
            ? matching
            : matching.stream().skip(pageable.getOffset()).limit(pageable.getPageSize()).toList();
    Pageable unsorted =
        pageable.isUnpaged()
            ? Pageable.unpaged()
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    return new PageImpl<>(toRows(page), unsorted, matching.size());
  }

  @Transactional(readOnly = true)
  public DriverComplianceCountsResponseDTO countQueues() {
    LocalDate today = SchoolTransportCredentialService.today();
    List<EvaluatedDriver> everyone = evaluate(drivers.findQueueCandidates(null, null, null), today);
    List<EvaluatedDriver> awaitingDecision =
        filter(everyone, DriverComplianceView.AWAITING_DECISION, today).toList();
    return new DriverComplianceCountsResponseDTO(
        awaitingDecision.size(),
        awaitingDecision.stream()
            .filter(candidate -> candidate.compliance().nextAction() == NextAction.DRIVER)
            .count(),
        filter(everyone, DriverComplianceView.PENDING_DOCUMENTS, today).count(),
        filter(everyone, DriverComplianceView.EXPIRING, today).count(),
        everyone.size(),
        everyone.stream()
            .filter(
                candidate ->
                    candidate.compliance().nextAction() == NextAction.READY_TO_APPROVE
                        || candidate.compliance().nextAction() == NextAction.ADMIN)
            .count(),
        today);
  }

  // Only the driver's own columns go to SQL; anything that depends on the evaluation is the view's.
  List<DriverModel> candidates(DriverComplianceView view, String search) {
    DriverApprovalStatus status =
        view == DriverComplianceView.AWAITING_DECISION ? DriverApprovalStatus.UNDER_REVIEW : null;
    String text =
        search == null || search.isBlank() ? null : search.strip().toLowerCase(Locale.ROOT);
    String plate = text == null ? null : VehicleService.normalizePlate(text);
    return drivers.findQueueCandidates(
        status,
        text == null ? null : contains(text),
        plate == null || plate.isEmpty() ? null : contains(plate));
  }

  static String contains(String value) {
    return "%" + value.replaceAll("([\\\\%_])", "\\\\$1") + "%";
  }

  List<EvaluatedDriver> evaluate(List<DriverModel> candidates, LocalDate today) {
    Map<Long, DriverComplianceDTO> evaluations = complianceService.evaluateAll(candidates, today);
    return candidates.stream()
        .map(driver -> new EvaluatedDriver(driver, evaluations.get(driver.getId())))
        .toList();
  }

  static Stream<EvaluatedDriver> filter(
      List<EvaluatedDriver> evaluated, DriverComplianceView view, LocalDate today) {
    return evaluated.stream().filter(candidate -> view.includes(candidate, today));
  }

  List<DriverComplianceRowResponseDTO> toRows(List<EvaluatedDriver> page) {
    if (page.isEmpty()) {
      return List.of();
    }
    List<Long> ids = page.stream().map(candidate -> candidate.driver().getId()).toList();
    Map<Long, String> cities =
        drivers.findCitiesByDriverIdIn(ids).stream()
            .collect(
                Collectors.toMap(
                    city -> city.getDriverId(),
                    city -> city.getCityName(),
                    (first, second) -> first));
    Map<Long, Long> vehicleCounts =
        vehicles.countActiveByDriverIdIn(ids).stream()
            .collect(Collectors.toMap(count -> count.getDriverId(), count -> count.getTotal()));
    return page.stream()
        .map(
            candidate ->
                mapper.toRow(
                    candidate.driver(),
                    candidate.compliance(),
                    cities.get(candidate.driver().getId()),
                    vehicleCounts.getOrDefault(candidate.driver().getId(), 0L)))
        .toList();
  }
}
