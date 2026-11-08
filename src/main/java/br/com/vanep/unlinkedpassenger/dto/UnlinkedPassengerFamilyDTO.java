package br.com.vanep.unlinkedpassenger.dto;

import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import java.time.Instant;

public record UnlinkedPassengerFamilyDTO(
    String vehicleToken,
    String dependentToken,
    Instant invitedAt,
    FamilyMatchResponseDTO familyMatch) {

  public static final UnlinkedPassengerFamilyDTO NONE =
      new UnlinkedPassengerFamilyDTO(null, null, null, null);
}
