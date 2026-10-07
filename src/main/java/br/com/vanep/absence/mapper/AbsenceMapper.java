package br.com.vanep.absence.mapper;

import br.com.vanep.absence.dto.AbsenceResponseDTO;
import br.com.vanep.absence.model.AbsenceModel;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AbsenceMapper {

  public List<AbsenceResponseDTO> toResponses(List<AbsenceModel> rows) {
    return rows.stream().map(this::toResponse).toList();
  }

  public AbsenceResponseDTO toResponse(AbsenceModel row) {
    return new AbsenceResponseDTO(
        row.getToken(),
        row.getDependent().getToken(),
        row.getClientDriver().getToken(),
        row.getTrip() == null ? null : row.getTrip().getToken(),
        row.getAbsenceDate(),
        row.getLeg(),
        row.getSource(),
        row.getReason(),
        row.getNotifiedAt(),
        row.getCreatedAt(),
        row.getUpdatedAt());
  }
}
