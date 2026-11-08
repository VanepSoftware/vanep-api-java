package br.com.vanep.contract.mapper;

import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.contract.dto.ContractItemResponseDTO;
import br.com.vanep.contract.dto.ContractResponseDTO;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.schedule.mapper.ScheduleMapper;
import br.com.vanep.shared.mapper.InstallmentMapper;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class ContractMapper {

  private final ScheduleMapper scheduleMapper;
  private final InstallmentMapper installmentMapper;

  public ContractMapper(ScheduleMapper scheduleMapper, InstallmentMapper installmentMapper) {
    this.scheduleMapper = scheduleMapper;
    this.installmentMapper = installmentMapper;
  }

  public ContractResponseDTO toResponse(
      ContractModel contract, Collection<ContractItemModel> contractItems) {
    ClientDriverModel link = contract.getClientDriver();
    return new ContractResponseDTO(
        contract.getToken(),
        link.getToken(),
        link.getClient().getToken(),
        link.getClient().getUser().getName(),
        link.getDriver().getToken(),
        link.getDriver().getUser().getName(),
        contract.getStatus(),
        contract.getStartsOn(),
        contract.getEndsOn(),
        contract.getTotalAmount(),
        contract.getInstallments(),
        installmentMapper.amountOf(contract.getTotalAmount(), contract.getInstallments()),
        installmentMapper.lastAmountOf(contract.getTotalAmount(), contract.getInstallments()),
        contract.getDueDay(),
        contractItems.stream().map(item -> toItemResponse(item)).toList(),
        contract.getCreatedAt(),
        contract.getUpdatedAt());
  }

  public ContractItemResponseDTO toItemResponse(ContractItemModel item) {
    return new ContractItemResponseDTO(
        item.getToken(),
        item.getDependent().getToken(),
        item.getDependent().getName(),
        item.getSchool().getToken(),
        item.getSchool().getName(),
        item.getPickupZipCode(),
        item.getPickupStreet(),
        item.getPickupNumber(),
        item.getPickupComplement(),
        item.getPickupNeighborhood(),
        item.getPickupCity().getToken(),
        item.getPickupCity().getName(),
        scheduleMapper.toSlotResponses(item.getSchedule()));
  }
}
