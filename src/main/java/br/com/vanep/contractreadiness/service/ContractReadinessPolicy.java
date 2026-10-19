package br.com.vanep.contractreadiness.service;

import br.com.vanep.contractreadiness.dto.ContractReadinessClientDTO;
import br.com.vanep.contractreadiness.dto.ContractReadinessDependentDTO;
import br.com.vanep.contractreadiness.dto.ContractReadinessMissingDTO;
import br.com.vanep.contractreadiness.enums.ContractReadinessField;
import br.com.vanep.contractreadiness.enums.ContractReadinessSubject;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContractReadinessPolicy {

  public List<ContractReadinessMissingDTO> findMissing(
      ContractReadinessClientDTO client, List<ContractReadinessDependentDTO> dependents) {
    List<ContractReadinessMissingDTO> missing = new ArrayList<>(findMissingOfClient(client));
    dependents.forEach(dependent -> missing.addAll(findMissingOfDependent(dependent)));
    return missing;
  }

  private List<ContractReadinessMissingDTO> findMissingOfClient(ContractReadinessClientDTO client) {
    List<ContractReadinessMissingDTO> missing = new ArrayList<>();
    if (isBlank(client.document())) {
      missing.add(ofClient(ContractReadinessField.DOCUMENT));
    }
    if (isBlank(client.phone())) {
      missing.add(ofClient(ContractReadinessField.PHONE));
    }
    if (client.addressId() == null) {
      missing.add(ofClient(ContractReadinessField.ADDRESS));
    }
    return missing;
  }

  private List<ContractReadinessMissingDTO> findMissingOfDependent(
      ContractReadinessDependentDTO dependent) {
    List<ContractReadinessMissingDTO> missing = new ArrayList<>();
    if (dependent.birthDate() == null) {
      missing.add(ofDependent(dependent, ContractReadinessField.BIRTH_DATE));
    }
    if (dependent.schoolId() == null) {
      missing.add(ofDependent(dependent, ContractReadinessField.SCHOOL));
    }
    if (dependent.addressId() == null) {
      missing.add(ofDependent(dependent, ContractReadinessField.ADDRESS));
    }
    if (dependent.schoolDays().isEmpty()) {
      missing.add(ofDependent(dependent, ContractReadinessField.SCHOOL_DAYS));
    }
    return missing;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private ContractReadinessMissingDTO ofClient(ContractReadinessField field) {
    return new ContractReadinessMissingDTO(ContractReadinessSubject.CLIENT, null, field);
  }

  private ContractReadinessMissingDTO ofDependent(
      ContractReadinessDependentDTO dependent, ContractReadinessField field) {
    return new ContractReadinessMissingDTO(
        ContractReadinessSubject.DEPENDENT, dependent.token(), field);
  }
}
