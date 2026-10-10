package br.com.vanep.contract.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
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
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.schedule.service.ScheduleService;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContractService {

  private final ContractRepository contracts;
  private final ContractItemRepository items;
  private final ClientDriverRepository links;
  private final DependentRepository dependents;
  private final SchoolRepository schools;
  private final AddressRepository addresses;
  private final ScheduleService schedules;
  private final ContractTermsPolicy termsPolicy;
  private final ContractTransitionPolicy transitionPolicy;
  private final LinkStatusPolicy linkStatusPolicy;
  private final ContractMapper mapper;
  private final MessageSource messages;

  public ContractService(
      ContractRepository contracts,
      ContractItemRepository items,
      ClientDriverRepository links,
      DependentRepository dependents,
      SchoolRepository schools,
      AddressRepository addresses,
      ScheduleService schedules,
      ContractTermsPolicy termsPolicy,
      ContractTransitionPolicy transitionPolicy,
      LinkStatusPolicy linkStatusPolicy,
      ContractMapper mapper,
      MessageSource messages) {
    this.contracts = contracts;
    this.items = items;
    this.links = links;
    this.dependents = dependents;
    this.schools = schools;
    this.addresses = addresses;
    this.schedules = schedules;
    this.termsPolicy = termsPolicy;
    this.transitionPolicy = transitionPolicy;
    this.linkStatusPolicy = linkStatusPolicy;
    this.mapper = mapper;
    this.messages = messages;
  }

  @Transactional
  public ContractResponseDTO create(ContractCreateRequestDTO request) {
    ClientDriverModel link = requireLink(request.clientDriverToken());
    ensureLinkCanBeContracted(link);
    ensureValidPeriod(request);
    ensureDistinctDependents(request.items());
    ensureNoActiveContract(link);

    ContractModel contract = new ContractModel();
    contract.setClientDriver(link);
    contract.setStatus(ContractStatus.ACTIVE);
    contract.setStartsOn(request.startsOn());
    contract.setEndsOn(request.endsOn());
    contract.setTotalAmount(request.totalAmount());
    contract.setInstallments(request.installments());
    contract.setDueDay(request.dueDay());
    request.items().forEach(itemRequest -> contract.addItem(createItem(link, itemRequest)));

    ContractModel saved = contracts.save(contract);
    recomputeLinkStatus(link);
    return mapper.toResponse(saved, saved.getItems());
  }

  @Transactional(readOnly = true)
  public ContractResponseDTO findByToken(String token) {
    return toResponse(requireContract(token));
  }

  @Transactional(readOnly = true)
  public List<ContractResponseDTO> findByClientDriverToken(String clientDriverToken) {
    ClientDriverModel link = requireLink(clientDriverToken);
    return toResponses(contracts.findByClientDriverId(link.getId()));
  }

  @Transactional(readOnly = true)
  public Page<ContractResponseDTO> findAll(Pageable pageable) {
    Page<ContractModel> page = contracts.findPage(pageable);
    Map<Long, List<ContractItemModel>> itemsByContract = findItemsByContract(page.getContent());
    return page.map(contract -> toResponse(contract, itemsByContract));
  }

  @Transactional
  public ContractResponseDTO update(String token, ContractUpdateRequestDTO request) {
    ContractModel contract = requireContract(token);
    if (request.status().isPresent()) {
      changeStatus(contract, request.status().get());
    }
    return toResponse(contract);
  }

  @Transactional
  public void delete(String token) {
    ContractModel contract = requireContract(token);
    contracts.delete(contract);
    recomputeLinkStatus(contract.getClientDriver());
  }

  @Transactional
  public ContractResponseDTO restore(String token) {
    if (!contracts.existsDeletedByToken(token)) {
      throw notFound("contract.not_found");
    }
    if (contracts.existsActiveConflictForRestore(token)) {
      throw conflict("contract.active_conflict");
    }
    contracts.restoreByToken(token);
    items.restoreByContractToken(token);
    items.restoreSchedulesByContractToken(token);
    items.restoreSlotsByContractToken(token);

    ContractModel contract = requireContract(token);
    List<ContractItemModel> restoredItems = items.findByContractIdIn(List.of(contract.getId()));
    if (ContractStatus.SIGNED_AND_NOT_ENDED.contains(contract.getStatus())) {
      restoredItems.forEach(item -> ensureNoSlotConflict(item));
    }
    recomputeLinkStatus(contract.getClientDriver());
    return mapper.toResponse(contract, restoredItems);
  }

  void changeStatus(ContractModel contract, ContractStatus target) {
    if (target == null) {
      throw badRequest("contract.status.required");
    }
    if (target == ContractStatus.SUPERSEDED) {
      throw unprocessableEntity("contract.status.requires_successor");
    }
    if (!transitionPolicy.allows(contract.getStatus(), target)) {
      throw conflict("contract.status.invalid_transition");
    }
    if (target == ContractStatus.ACTIVE) {
      ensureNoActiveContract(contract.getClientDriver());
    }
    contract.setStatus(target);
    contracts.save(contract);
    recomputeLinkStatus(contract.getClientDriver());
  }

  ContractItemModel createItem(ClientDriverModel link, ContractItemRequestDTO request) {
    DependentModel dependent =
        dependents
            .findByToken(request.dependentToken())
            .orElseThrow(() -> notFound("dependent.not_found"));
    if (!link.getClient().getId().equals(dependent.getClientId())) {
      throw unprocessableEntity("contract.item.dependent_not_in_link");
    }
    SchoolModel school = findSchoolOf(dependent);
    AddressModel address = findAddressOf(dependent);

    ContractItemModel item = new ContractItemModel();
    item.setDependent(dependent);
    item.setSchool(school);
    copyPickupAddress(address, item);
    item.setMonthlyAmount(request.monthlyAmount());
    item.setSchedule(schedules.create(request.slots()));
    ensureNoSlotConflict(item);
    return item;
  }

  SchoolModel findSchoolOf(DependentModel dependent) {
    if (dependent.getSchoolId() == null) {
      throw unprocessableEntity("contract.item.school_required");
    }
    return schools
        .findById(dependent.getSchoolId())
        .orElseThrow(() -> unprocessableEntity("contract.item.school_required"));
  }

  AddressModel findAddressOf(DependentModel dependent) {
    if (dependent.getAddressId() == null) {
      throw unprocessableEntity("contract.item.address_required");
    }
    return addresses
        .findById(dependent.getAddressId())
        .orElseThrow(() -> unprocessableEntity("contract.item.address_required"));
  }

  void copyPickupAddress(AddressModel address, ContractItemModel item) {
    item.setPickupCity(address.getCity());
    item.setPickupZipCode(address.getZipCode());
    item.setPickupStreet(address.getStreet());
    item.setPickupNumber(address.getNumber());
    item.setPickupComplement(address.getComplement());
    item.setPickupNeighborhood(address.getNeighborhood());
    item.setPickupDistrict(address.getDistrict());
    item.setPickupGooglePlaceId(address.getGooglePlaceId());
  }

  void ensureLinkCanBeContracted(ClientDriverModel link) {
    if (link.getDriver().getApprovalStatus() != DriverApprovalStatus.APPROVED) {
      throw unprocessableEntity("contract.driver.not_approved");
    }
    if (link.getStatus() == RelationshipStatus.BLOCKED) {
      throw unprocessableEntity("contract.link.blocked");
    }
  }

  void ensureValidPeriod(ContractCreateRequestDTO request) {
    termsPolicy
        .validate(request.startsOn(), request.endsOn())
        .ifPresent(
            violation -> {
              throw unprocessableEntity(violation.messageKey());
            });
  }

  void ensureDistinctDependents(List<ContractItemRequestDTO> itemRequests) {
    long distinctDependents =
        itemRequests.stream().map(itemRequest -> itemRequest.dependentToken()).distinct().count();
    if (distinctDependents < itemRequests.size()) {
      throw badRequest("contract.item.duplicate_dependent");
    }
  }

  void ensureNoActiveContract(ClientDriverModel link) {
    if (contracts.findActiveByClientDriverId(link.getId()).isPresent()) {
      throw conflict("contract.active_conflict");
    }
  }

  void ensureNoSlotConflict(ContractItemModel item) {
    Long ownScheduleId = item.getSchedule().getId();
    List<ScheduleSlotModel> taken =
        items
            .findSlotsByDependentIdAndContractStatusIn(
                item.getDependent().getId(), ContractStatus.SIGNED_AND_NOT_ENDED)
            .stream()
            .filter(slot -> !Objects.equals(slot.getSchedule().getId(), ownScheduleId))
            .toList();
    boolean collides =
        item.getSchedule().getSlots().stream()
            .anyMatch(
                slot ->
                    taken.stream()
                        .anyMatch(
                            takenSlot ->
                                takenSlot.getWeekday() == slot.getWeekday()
                                    && takenSlot.getLeg() == slot.getLeg()));
    if (collides) {
      throw conflict("contract.item.slot_conflict");
    }
  }

  void recomputeLinkStatus(ClientDriverModel link) {
    link.setStatus(
        linkStatusPolicy.deriveStatus(
            link.getStatus(), contracts.findStatusesByClientDriverId(link.getId())));
    links.save(link);
  }

  ContractResponseDTO toResponse(ContractModel contract) {
    return mapper.toResponse(contract, items.findByContractIdIn(List.of(contract.getId())));
  }

  List<ContractResponseDTO> toResponses(List<ContractModel> contractList) {
    Map<Long, List<ContractItemModel>> itemsByContract = findItemsByContract(contractList);
    return contractList.stream().map(contract -> toResponse(contract, itemsByContract)).toList();
  }

  ContractResponseDTO toResponse(
      ContractModel contract, Map<Long, List<ContractItemModel>> itemsByContract) {
    return mapper.toResponse(contract, itemsByContract.getOrDefault(contract.getId(), List.of()));
  }

  Map<Long, List<ContractItemModel>> findItemsByContract(List<ContractModel> contractList) {
    if (contractList.isEmpty()) {
      return Map.of();
    }
    List<Long> contractIds = contractList.stream().map(contract -> contract.getId()).toList();
    return items.findByContractIdIn(contractIds).stream()
        .collect(Collectors.groupingBy(item -> item.getContract().getId()));
  }

  ContractModel requireContract(String token) {
    return contracts.findByToken(token).orElseThrow(() -> notFound("contract.not_found"));
  }

  ClientDriverModel requireLink(String token) {
    return links.findByToken(token).orElseThrow(() -> notFound("client_driver.not_found"));
  }

  ResponseStatusException conflict(String key) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message(key));
  }

  ResponseStatusException notFound(String key) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message(key));
  }

  ResponseStatusException badRequest(String key) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message(key));
  }

  ResponseStatusException unprocessableEntity(String key) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message(key));
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
