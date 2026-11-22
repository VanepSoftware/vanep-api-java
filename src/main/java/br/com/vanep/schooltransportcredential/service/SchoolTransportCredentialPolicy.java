package br.com.vanep.schooltransportcredential.service;

import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.schooltransportcredential.dto.CredentialDTO;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsDTO;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.model.DocumentSubmission;
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

  public static final Map<CredentialRequirement, DriverDocumentType> DRIVER_DOCUMENT_TYPES =
      Map.of(
          CredentialRequirement.SCHOOL_DRIVER_COURSE, DriverDocumentType.SCHOOL_DRIVER_COURSE,
          CredentialRequirement.CRIMINAL_RECORD, DriverDocumentType.CRIMINAL_RECORD,
          CredentialRequirement.MUNICIPAL_AUTHORIZATION,
              DriverDocumentType.MUNICIPAL_AUTHORIZATION);

  public static final Map<CredentialRequirement, VehicleDocumentType> VEHICLE_DOCUMENT_TYPES =
      Map.of(
          CredentialRequirement.VEHICLE_REGISTRATION, VehicleDocumentType.CRLV,
          CredentialRequirement.VEHICLE_INSPECTION, VehicleDocumentType.VEHICLE_INSPECTION,
          CredentialRequirement.SCHOOL_VEHICLE_AUTHORIZATION,
              VehicleDocumentType.SCHOOL_VEHICLE_AUTHORIZATION);

  public static CredentialRequirement requirementOf(DocumentSubmission submission) {
    return switch (submission) {
      case DriverCnhModel _ -> CredentialRequirement.DRIVER_LICENSE;
      case DriverDocumentModel document ->
          requirementOf(DRIVER_DOCUMENT_TYPES, document.getDocumentType());
      case VehicleDocumentModel document ->
          requirementOf(VEHICLE_DOCUMENT_TYPES, document.getDocumentType());
      default -> throw new IllegalArgumentException("Unknown submission " + submission.kind());
    };
  }

  static <T> CredentialRequirement requirementOf(Map<CredentialRequirement, T> types, T type) {
    return types.entrySet().stream()
        .filter(entry -> entry.getValue() == type)
        .map(entry -> entry.getKey())
        .findFirst()
        .orElseThrow();
  }

  // A null expiry means the document does not expire, so it outranks any dated one.
  private static final Comparator<DocumentSubmission> FARTHEST_EXPIRY_THEN_NEWEST =
      Comparator.comparing(
              (DocumentSubmission submission) -> submission.expiresOn(),
              Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
          .thenComparing(
              submission -> submission.getCreatedAt(),
              Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));

  public SchoolTransportCredentialsDTO evaluate(
      List<DriverCnhModel> cnhs,
      List<DriverDocumentModel> driverDocuments,
      List<VehicleDocumentModel> vehicleDocuments,
      LocalDate date) {
    List<CredentialDTO> credentials = new ArrayList<>();
    Set<CredentialRequirement> failed = EnumSet.noneOf(CredentialRequirement.class);
    for (CredentialRequirement requirement : CredentialRequirement.values()) {
      Optional<DocumentSubmission> satisfying =
          findSatisfying(
              requirement,
              submissionsOf(requirement, cnhs, driverDocuments, vehicleDocuments),
              date);
      satisfying.ifPresent(
          submission ->
              credentials.add(
                  new CredentialDTO(requirement, submission.number(), submission.expiresOn())));
      if (satisfying.isEmpty() && !requirement.isOptional()) {
        failed.add(requirement);
      }
    }
    return new SchoolTransportCredentialsDTO(
        List.copyOf(credentials), Collections.unmodifiableSet(failed));
  }

  public Optional<DocumentSubmission> findSatisfying(
      CredentialRequirement requirement, List<DocumentSubmission> submissions, LocalDate date) {
    return submissions.stream()
        .filter(submission -> submission.getReview().getStatus() == DocumentStatus.APPROVED)
        .filter(submission -> isValidOrUndated(submission, requirement, date))
        .filter(submission -> !lacksSchoolTransportCategory(submission))
        .max(FARTHEST_EXPIRY_THEN_NEWEST);
  }

  public List<DocumentSubmission> submissionsOf(
      CredentialRequirement requirement,
      List<DriverCnhModel> cnhs,
      List<DriverDocumentModel> driverDocuments,
      List<VehicleDocumentModel> vehicleDocuments) {
    if (requirement == CredentialRequirement.DRIVER_LICENSE) {
      return cnhs.stream()
          .filter(cnh -> cnh.isActive())
          .<DocumentSubmission>map(cnh -> cnh)
          .toList();
    }
    if (DRIVER_DOCUMENT_TYPES.containsKey(requirement)) {
      DriverDocumentType type = DRIVER_DOCUMENT_TYPES.get(requirement);
      return driverDocuments.stream()
          .filter(document -> document.isActive() && document.getDocumentType() == type)
          .<DocumentSubmission>map(document -> document)
          .toList();
    }
    VehicleDocumentType type = VEHICLE_DOCUMENT_TYPES.get(requirement);
    return vehicleDocuments.stream()
        .filter(document -> document.getDocumentType() == type)
        .<DocumentSubmission>map(document -> document)
        .toList();
  }

  // A CNH sent with only its file has no category until the reviewer approves it.
  public static boolean lacksSchoolTransportCategory(DocumentSubmission submission) {
    return submission instanceof DriverCnhModel cnh
        && cnh.getCategory() != null
        && !hasSchoolTransportCategory(cnh.getCategory());
  }

  // CTB art. 138 II: school transport needs category D or E, alone or with A (AD, AE).
  public static boolean hasSchoolTransportCategory(String category) {
    return category != null && (category.contains("D") || category.contains("E"));
  }

  // A license always has a validity; a driver or van document without expiry never expires.
  static boolean isValidOrUndated(
      DocumentSubmission submission, CredentialRequirement requirement, LocalDate date) {
    if (submission.expiresOn() == null) {
      return requirement != CredentialRequirement.DRIVER_LICENSE;
    }
    return isValidOn(submission.expiresOn(), date);
  }

  public static boolean isValidOn(LocalDate validUntil, LocalDate date) {
    return validUntil != null && !validUntil.isBefore(date);
  }
}
