package br.com.vanep.unlinkedpassenger.dto;

import br.com.vanep.familymatch.dto.FamilyMatchResponseDTO;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record UnlinkedPassengerFamilyDTO(
    @Nullable String vehicleToken,
    @Nullable String dependentToken,
    @Nullable Instant invitedAt,
    @Nullable FamilyMatchResponseDTO familyMatch) {

  public static final UnlinkedPassengerFamilyDTO NONE =
      new UnlinkedPassengerFamilyDTO(null, null, null, null);
}
