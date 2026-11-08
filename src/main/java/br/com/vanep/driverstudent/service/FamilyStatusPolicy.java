package br.com.vanep.driverstudent.service;

import br.com.vanep.driverstudent.enums.FamilyStatus;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerFamilyDTO;
import org.springframework.stereotype.Component;

@Component
public class FamilyStatusPolicy {

  public FamilyStatus deriveUnlinkedStatus(UnlinkedPassengerFamilyDTO family) {
    if (family.dependentToken() != null) {
      return FamilyStatus.WITH_FAMILY;
    }
    if (family.familyMatch() != null) {
      return FamilyStatus.FAMILY_JOINED;
    }
    if (family.invitedAt() != null) {
      return FamilyStatus.INVITED;
    }
    return FamilyStatus.NOT_INVITED;
  }
}
