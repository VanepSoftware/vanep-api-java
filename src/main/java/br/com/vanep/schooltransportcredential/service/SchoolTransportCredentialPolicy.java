package br.com.vanep.schooltransportcredential.service;

import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.schooltransportcredential.dto.CredentialDTO;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsDTO;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SchoolTransportCredentialPolicy {

  static final Map<CredentialRequirement, DriverDocumentType> DRIVER_DOCUMENT_TYPES =
      Map.of(
          CredentialRequirement.SCHOOL_DRIVER_COURSE, DriverDocumentType.SCHOOL_DRIVER_COURSE,
          CredentialRequirement.CRIMINAL_RECORD, DriverDocumentType.CRIMINAL_RECORD,
          CredentialRequirement.MUNICIPAL_AUTHORIZATION,
              DriverDocumentType.MUNICIPAL_AUTHORIZATION);

  static final Map<CredentialRequirement, VehicleDocumentType> VEHICLE_DOCUMENT_TYPES =
      Map.of(
          CredentialRequirement.VEHICLE_REGISTRATION, VehicleDocumentType.CRLV,
          CredentialRequirement.VEHICLE_INSPECTION, VehicleDocumentType.VEHICLE_INSPECTION,
          CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION,
              VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION);

  // A null expiry means the document does not expire, so it outranks any dated one.
  private static final Comparator<Candidate> FARTHEST_EXPIRY_THEN_NEWEST =
      Comparator.comparing(
              (Candidate candidate) -> candidate.validUntil(),
              Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
          .thenComparing(
              candidate -> candidate.createdAt(),
              Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));

  public SchoolTransportCredentialsDTO evaluate(
      List<DriverCnhModel> cnhs,
      List<DriverDocumentModel> driverDocuments,
      List<VehicleDocumentModel> vehicleDocuments,
      LocalDate date) {
    List<CredentialDTO> credentials = new ArrayList<>();
    Set<CredentialRequirement> failed = EnumSet.noneOf(CredentialRequirement.class);
    for (CredentialRequirement requirement : CredentialRequirement.values()) {
      Optional<CredentialDTO> credential =
          findCredential(requirement, cnhs, driverDocuments, vehicleDocuments, date);
      credential.ifPresent(found -> credentials.add(found));
      if (credential.isEmpty() && !requirement.isOptional()) {
        failed.add(requirement);
      }
    }
    return new SchoolTransportCredentialsDTO(
        List.copyOf(credentials), Collections.unmodifiableSet(failed));
  }

  Optional<CredentialDTO> findCredential(
      CredentialRequirement requirement,
      List<DriverCnhModel> cnhs,
      List<DriverDocumentModel> driverDocuments,
      List<VehicleDocumentModel> vehicleDocuments,
      LocalDate date) {
    if (requirement == CredentialRequirement.DRIVER_LICENSE) {
      return findDriverLicense(cnhs, date);
    }
    if (DRIVER_DOCUMENT_TYPES.containsKey(requirement)) {
      DriverDocumentType type = DRIVER_DOCUMENT_TYPES.get(requirement);
      return pickBest(
          requirement,
          driverDocuments.stream()
              .filter(document -> document.isActive() && document.getDocumentType() == type)
              .map(
                  document ->
                      new Candidate(
                          document.getDocumentNumber(),
                          document.getExpiresAt(),
                          document.getCreatedAt(),
                          document.getReview()))
              .toList(),
          date);
    }
    VehicleDocumentType type = VEHICLE_DOCUMENT_TYPES.get(requirement);
    return pickBest(
        requirement,
        vehicleDocuments.stream()
            .filter(document -> document.getDocumentType() == type)
            .map(
                document ->
                    new Candidate(
                        document.getDocumentNumber(),
                        document.getExpiresAt(),
                        document.getCreatedAt(),
                        document.getReview()))
            .toList(),
        date);
  }

  static Optional<CredentialDTO> findDriverLicense(List<DriverCnhModel> cnhs, LocalDate date) {
    return cnhs.stream()
        .filter(cnh -> cnh.isActive() && cnh.getReview().getStatus() == DocumentStatus.APPROVED)
        .filter(cnh -> isValidOn(cnh.getValidUntil(), date))
        .filter(cnh -> hasSchoolTransportCategory(cnh.getCategory()))
        .max(Comparator.comparing(cnh -> cnh.getValidUntil()))
        .map(
            cnh ->
                new CredentialDTO(
                    CredentialRequirement.DRIVER_LICENSE,
                    cnh.getRegistrationNumber(),
                    cnh.getValidUntil()));
  }

  // CTB art. 138 II: school transport needs category D or E, alone or with A (AD, AE).
  public static boolean hasSchoolTransportCategory(String category) {
    return category != null && (category.contains("D") || category.contains("E"));
  }

  static Optional<CredentialDTO> pickBest(
      CredentialRequirement requirement, List<Candidate> candidates, LocalDate date) {
    return candidates.stream()
        .filter(candidate -> candidate.review().getStatus() == DocumentStatus.APPROVED)
        .filter(
            candidate -> candidate.validUntil() == null || isValidOn(candidate.validUntil(), date))
        .max(FARTHEST_EXPIRY_THEN_NEWEST)
        .map(
            candidate ->
                new CredentialDTO(requirement, candidate.number(), candidate.validUntil()));
  }

  static boolean isValidOn(LocalDate validUntil, LocalDate date) {
    return validUntil != null && !validUntil.isBefore(date);
  }

  record Candidate(String number, LocalDate validUntil, Instant createdAt, DocumentReview review) {}
}
