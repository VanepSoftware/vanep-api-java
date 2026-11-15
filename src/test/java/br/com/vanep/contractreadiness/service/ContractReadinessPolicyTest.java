package br.com.vanep.contractreadiness.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.contractreadiness.dto.ContractReadinessClientDTO;
import br.com.vanep.contractreadiness.dto.ContractReadinessDependentDTO;
import br.com.vanep.contractreadiness.dto.ContractReadinessMissingDTO;
import br.com.vanep.contractreadiness.enums.ContractReadinessField;
import br.com.vanep.contractreadiness.enums.ContractReadinessSubject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ContractReadinessPolicyTest {

  private static final ContractReadinessClientDTO READY_CLIENT =
      new ContractReadinessClientDTO("11144477735", "11999990000", 1L);
  private static final ContractReadinessDependentDTO READY_LUCAS =
      new ContractReadinessDependentDTO(
          "lucas", LocalDate.of(2016, 4, 2), 7L, 2L, List.of(DayOfWeek.MONDAY));

  private final ContractReadinessPolicy policy = new ContractReadinessPolicy();

  @Test
  void nothingIsMissingWhenTheClientAndTheDependentsHaveEverything() {
    assertThat(policy.findMissing(READY_CLIENT, List.of(READY_LUCAS))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void aClientWithoutDocumentMissesTheDocument(String document) {
    ContractReadinessClientDTO client = new ContractReadinessClientDTO(document, "11999990000", 1L);

    assertThat(policy.findMissing(client, List.of(READY_LUCAS)))
        .containsExactly(ofClient(ContractReadinessField.DOCUMENT));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void aClientWithoutPhoneMissesThePhone(String phone) {
    ContractReadinessClientDTO client = new ContractReadinessClientDTO("11144477735", phone, 1L);

    assertThat(policy.findMissing(client, List.of(READY_LUCAS)))
        .containsExactly(ofClient(ContractReadinessField.PHONE));
  }

  @Test
  void aClientWithoutPersonalAddressMissesTheAddress() {
    ContractReadinessClientDTO client =
        new ContractReadinessClientDTO("11144477735", "11999990000", null);

    assertThat(policy.findMissing(client, List.of(READY_LUCAS)))
        .containsExactly(ofClient(ContractReadinessField.ADDRESS));
  }

  @Test
  void aDependentWithoutBirthDateMissesTheBirthDate() {
    ContractReadinessDependentDTO lucas =
        new ContractReadinessDependentDTO("lucas", null, 7L, 2L, List.of(DayOfWeek.MONDAY));

    assertThat(policy.findMissing(READY_CLIENT, List.of(lucas)))
        .containsExactly(ofDependent("lucas", ContractReadinessField.BIRTH_DATE));
  }

  @Test
  void aDependentWithoutSchoolMissesTheSchool() {
    ContractReadinessDependentDTO lucas =
        new ContractReadinessDependentDTO(
            "lucas", LocalDate.of(2016, 4, 2), null, 2L, List.of(DayOfWeek.MONDAY));

    assertThat(policy.findMissing(READY_CLIENT, List.of(lucas)))
        .containsExactly(ofDependent("lucas", ContractReadinessField.SCHOOL));
  }

  @Test
  void aDependentWithoutAddressMissesTheAddress() {
    ContractReadinessDependentDTO lucas =
        new ContractReadinessDependentDTO(
            "lucas", LocalDate.of(2016, 4, 2), 7L, null, List.of(DayOfWeek.MONDAY));

    assertThat(policy.findMissing(READY_CLIENT, List.of(lucas)))
        .containsExactly(ofDependent("lucas", ContractReadinessField.ADDRESS));
  }

  @Test
  void aDependentWithoutSchoolGridMissesTheSchoolDays() {
    ContractReadinessDependentDTO lucas =
        new ContractReadinessDependentDTO("lucas", LocalDate.of(2016, 4, 2), 7L, 2L, List.of());

    assertThat(policy.findMissing(READY_CLIENT, List.of(lucas)))
        .containsExactly(ofDependent("lucas", ContractReadinessField.SCHOOL_DAYS));
  }

  @Test
  void severalMissingDataComeInTheOrderOfTheTable() {
    ContractReadinessClientDTO client = new ContractReadinessClientDTO("11144477735", null, null);
    ContractReadinessDependentDTO lucas =
        new ContractReadinessDependentDTO("lucas", null, null, null, List.of());
    ContractReadinessDependentDTO marina =
        new ContractReadinessDependentDTO(
            "marina", LocalDate.of(2018, 9, 12), 7L, null, List.of(DayOfWeek.FRIDAY));

    assertThat(policy.findMissing(client, List.of(lucas, READY_LUCAS, marina)))
        .containsExactly(
            ofClient(ContractReadinessField.PHONE),
            ofClient(ContractReadinessField.ADDRESS),
            ofDependent("lucas", ContractReadinessField.BIRTH_DATE),
            ofDependent("lucas", ContractReadinessField.SCHOOL),
            ofDependent("lucas", ContractReadinessField.ADDRESS),
            ofDependent("lucas", ContractReadinessField.SCHOOL_DAYS),
            ofDependent("marina", ContractReadinessField.ADDRESS));
  }

  private ContractReadinessMissingDTO ofClient(ContractReadinessField field) {
    return new ContractReadinessMissingDTO(ContractReadinessSubject.CLIENT, null, field);
  }

  private ContractReadinessMissingDTO ofDependent(String token, ContractReadinessField field) {
    return new ContractReadinessMissingDTO(ContractReadinessSubject.DEPENDENT, token, field);
  }
}
