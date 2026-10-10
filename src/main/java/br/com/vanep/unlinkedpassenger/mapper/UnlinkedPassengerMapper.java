package br.com.vanep.unlinkedpassenger.mapper;

import br.com.vanep.address.mapper.AddressMapper;
import br.com.vanep.schedule.mapper.ScheduleMapper;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerResponseDTO;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import org.springframework.stereotype.Component;

@Component
public class UnlinkedPassengerMapper {

  private final AddressMapper addressMapper;
  private final ScheduleMapper scheduleMapper;

  public UnlinkedPassengerMapper(AddressMapper addressMapper, ScheduleMapper scheduleMapper) {
    this.addressMapper = addressMapper;
    this.scheduleMapper = scheduleMapper;
  }

  public UnlinkedPassengerResponseDTO toResponse(UnlinkedPassengerModel passenger) {
    return new UnlinkedPassengerResponseDTO(
        passenger.getToken(),
        passenger.getName(),
        passenger.getSchool().getToken(),
        passenger.getSchool().getName(),
        passenger.getSchoolShift(),
        addressMapper.toResponse(passenger.getAddress()),
        passenger.getNotes(),
        scheduleMapper.toSlotResponses(passenger.getSchedule()),
        passenger.getCreatedAt(),
        passenger.getUpdatedAt());
  }
}
