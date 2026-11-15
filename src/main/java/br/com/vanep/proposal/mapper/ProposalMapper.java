package br.com.vanep.proposal.mapper;

import br.com.vanep.proposal.dto.ProposalAdjustmentRequestDTO;
import br.com.vanep.proposal.dto.ProposalResponseDTO;
import br.com.vanep.proposal.dto.ProposalSummaryResponseDTO;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.proposal.model.ProposalAdjustmentModel;
import br.com.vanep.proposal.model.ProposalItemModel;
import br.com.vanep.proposal.model.ProposalModel;
import br.com.vanep.proposal.repository.ProposalRepository;
import br.com.vanep.schedule.mapper.ScheduleMapper;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.servicerequest.mapper.ServiceRequestMapper;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.shared.mapper.InstallmentMapper;
import br.com.vanep.vehicle.model.VehicleModel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProposalMapper {

  private final ScheduleMapper scheduleMapper;
  private final ServiceRequestMapper requestMapper;
  private final InstallmentMapper installmentMapper;

  public ProposalMapper(
      ScheduleMapper scheduleMapper,
      ServiceRequestMapper requestMapper,
      InstallmentMapper installmentMapper) {
    this.scheduleMapper = scheduleMapper;
    this.requestMapper = requestMapper;
    this.installmentMapper = installmentMapper;
  }

  public ProposalModel.Snapshot toSnapshot(VehicleModel vehicle) {
    return new ProposalModel.Snapshot(
        ProposalModel.Snapshot.CURRENT_VERSION,
        new ProposalModel.Snapshot.Vehicle(
            vehicle.getPlate(),
            vehicle.getBrand(),
            vehicle.getModel(),
            vehicle.getManufactureYear(),
            vehicle.getColor(),
            vehicle.getCapacity()));
  }

  public ProposalItemModel toItemModel(Long dependentId, ScheduleModel schedule) {
    ProposalItemModel item = new ProposalItemModel();
    item.setDependentId(dependentId);
    item.setSchedule(schedule);
    return item;
  }

  public ProposalAdjustmentModel toAdjustmentModel(
      Long proposalId, ProposalAdjustmentRequestDTO request) {
    ProposalAdjustmentModel adjustment = new ProposalAdjustmentModel();
    adjustment.setProposalId(proposalId);
    adjustment.setField(request.field());
    adjustment.setSuggestedValue(stripToNull(request.suggestedValue()));
    adjustment.setNote(stripToNull(request.note()));
    return adjustment;
  }

  public ProposalResponseDTO toClientResponse(
      ProposalModel proposal,
      ServiceRequestModel request,
      Map<Long, String> dependentTokens,
      List<ProposalAdjustmentModel> adjustments) {
    Map<Long, String> names = requestedNamesOf(request);
    return toResponse(
        proposal,
        request,
        adjustments,
        null,
        item ->
            toItemResponse(
                item,
                dependentTokens.get(item.getDependentId()),
                names.get(item.getDependentId()),
                requestMapper.firstNameOf(names.get(item.getDependentId()))));
  }

  public ProposalResponseDTO toDriverResponse(
      ProposalModel proposal,
      ServiceRequestModel request,
      Map<Long, String> dependentTokens,
      List<ProposalAdjustmentModel> adjustments,
      String vehicleToken) {
    Map<Long, String> names = requestedNamesOf(request);
    return toResponse(
        proposal,
        request,
        adjustments,
        vehicleToken,
        item ->
            toItemResponse(
                item,
                dependentTokens.get(item.getDependentId()),
                null,
                requestMapper.firstNameOf(names.get(item.getDependentId()))));
  }

  ProposalResponseDTO toResponse(
      ProposalModel proposal,
      ServiceRequestModel request,
      List<ProposalAdjustmentModel> adjustments,
      String vehicleToken,
      Function<ProposalItemModel, ProposalResponseDTO.Item> toItem) {
    return new ProposalResponseDTO(
        proposal.getToken(),
        request.getToken(),
        proposal.getClientDriver().getToken(),
        proposal.getVersion(),
        proposal.getStatus(),
        proposal.getValidUntil(),
        proposal.getStartsOn(),
        proposal.getEndsOn(),
        proposal.getTotalAmount(),
        proposal.getInstallments(),
        installmentMapper.amountOf(proposal.getTotalAmount(), proposal.getInstallments()),
        installmentMapper.lastAmountOf(proposal.getTotalAmount(), proposal.getInstallments()),
        proposal.getDueDay(),
        proposal.getEnrollmentFee(),
        proposal.getAdjustmentPolicy(),
        proposal.getCancellationPolicy(),
        toVehicleResponse(vehicleToken, proposal.getSnapshot().vehicle()),
        proposal.getMessage(),
        proposal.getDeclineReason(),
        proposal.getAcceptedAt(),
        proposal.getItems().stream()
            .sorted(Comparator.comparing(item -> item.getId()))
            .map(toItem)
            .toList(),
        adjustments.stream().map(adjustment -> toAdjustmentResponse(adjustment)).toList(),
        proposal.getCreatedAt(),
        proposal.getUpdatedAt());
  }

  public ProposalSummaryResponseDTO toSummaryResponse(ProposalRepository.CurrentProposalRow row) {
    return new ProposalSummaryResponseDTO(
        row.getToken(),
        row.getVersion(),
        row.getStatus(),
        row.getTotalAmount(),
        row.getInstallments(),
        installmentMapper.amountOf(row.getTotalAmount(), row.getInstallments()),
        installmentMapper.lastAmountOf(row.getTotalAmount(), row.getInstallments()),
        row.getValidUntil(),
        row.getStartsOn(),
        row.getHasAdjustments() && row.getStatus() == ProposalStatus.SENT);
  }

  ProposalResponseDTO.Item toItemResponse(
      ProposalItemModel item, String dependentToken, String fullName, String firstName) {
    return new ProposalResponseDTO.Item(
        dependentToken, fullName, firstName, scheduleMapper.toSlotResponses(item.getSchedule()));
  }

  ProposalResponseDTO.Vehicle toVehicleResponse(
      String token, ProposalModel.Snapshot.Vehicle vehicle) {
    return new ProposalResponseDTO.Vehicle(
        token,
        vehicle.plate(),
        vehicle.brand(),
        vehicle.model(),
        vehicle.manufactureYear(),
        vehicle.color(),
        vehicle.capacity());
  }

  ProposalResponseDTO.Adjustment toAdjustmentResponse(ProposalAdjustmentModel adjustment) {
    return new ProposalResponseDTO.Adjustment(
        adjustment.getField(),
        adjustment.getSuggestedValue(),
        adjustment.getNote(),
        adjustment.getCreatedAt());
  }

  Map<Long, String> requestedNamesOf(ServiceRequestModel request) {
    return request.getItems().stream()
        .collect(
            Collectors.toMap(
                item -> item.getDependentId(), item -> item.getSnapshot().dependent().name()));
  }

  String stripToNull(String value) {
    return StringUtils.hasText(value) ? value.strip() : null;
  }
}
