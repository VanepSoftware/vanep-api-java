package br.com.vanep.dependent.mapper;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.dependent.dto.DependentClientDTO;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentSchoolDTO;
import br.com.vanep.dependent.dto.DependentSchoolDayRequestDTO;
import br.com.vanep.dependent.dto.DependentSchoolDayResponseDTO;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DependentMapper {

  static final Comparator<DependentSchoolDayModel> BY_WEEKDAY =
      Comparator.comparing((DependentSchoolDayModel day) -> day.getWeekday());

  public DependentModel toModel(DependentCreateDTO dto, Long clientId) {
    DependentModel model = new DependentModel();
    model.setClientId(clientId);
    model.setName(dto.getName());
    model.setBirthDate(dto.getBirthDate());
    model.setGender(dto.getGender());
    model.setPhone(dto.getPhone());
    model.setEmail(dto.getEmail());
    model.setSelf(Boolean.TRUE.equals(dto.getIsSelf()));
    return model;
  }

  public DependentResponseDTO toResponse(
      DependentModel model,
      String clientToken,
      DependentSchoolDTO school,
      AddressResponseDTO address) {
    return new DependentResponseDTO(
        model.getToken(),
        new DependentClientDTO(clientToken),
        model.getName(),
        model.getBirthDate(),
        model.getGender(),
        model.getDocument(),
        model.getPhone(),
        model.getEmail(),
        model.isSelf(),
        model.isDefaultDependent(),
        school,
        address,
        model.getCreatedAt(),
        model.getUpdatedAt());
  }

  public DependentSchoolDayModel toSchoolDayModel(
      Long dependentId, DependentSchoolDayRequestDTO request) {
    DependentSchoolDayModel day = new DependentSchoolDayModel();
    day.setDependentId(dependentId);
    day.setWeekday(request.weekday());
    day.setSchoolShift(request.schoolShift());
    return day;
  }

  public List<DependentSchoolDayResponseDTO> toSchoolDayResponses(
      List<DependentSchoolDayModel> days) {
    return days.stream()
        .sorted(BY_WEEKDAY)
        .map(day -> new DependentSchoolDayResponseDTO(day.getWeekday(), day.getSchoolShift()))
        .toList();
  }
}
