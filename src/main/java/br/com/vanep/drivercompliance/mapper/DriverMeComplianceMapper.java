package br.com.vanep.drivercompliance.mapper;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.RequirementDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.VehicleDTO;
import br.com.vanep.drivercompliance.dto.DriverMeComplianceResponseDTO;
import br.com.vanep.drivercompliance.dto.DriverMeComplianceResponseDTO.RequirementResponse;
import br.com.vanep.drivercompliance.dto.DriverMeComplianceResponseDTO.SubmissionResponse;
import br.com.vanep.drivercompliance.dto.DriverMeComplianceResponseDTO.VehicleResponse;
import br.com.vanep.drivercompliance.enums.ComplianceState;
import br.com.vanep.drivercompliance.enums.DriverComplianceView;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.shared.document.mapper.DocumentFileMapper;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.vehicle.model.VehicleModel;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DriverMeComplianceMapper {

  public DriverMeComplianceResponseDTO toResponse(
      DriverModel driver, DriverComplianceDTO compliance, LocalDate asOf) {
    return new DriverMeComplianceResponseDTO(
        asOf,
        driver.getApprovalStatus(),
        toRequirements(compliance.driverRequirements(), asOf),
        compliance.vehicles().stream().map(vehicle -> toVehicle(vehicle, asOf)).toList());
  }

  VehicleResponse toVehicle(VehicleDTO entry, LocalDate asOf) {
    VehicleModel vehicle = entry.vehicle();
    return new VehicleResponse(
        vehicle.getToken(),
        vehicle.getPlate(),
        vehicle.getBrand(),
        vehicle.getModel(),
        entry.qualified(),
        toRequirements(entry.requirements(), asOf));
  }

  List<RequirementResponse> toRequirements(List<RequirementDTO> entries, LocalDate asOf) {
    return entries.stream().map(entry -> toRequirement(entry, asOf)).toList();
  }

  RequirementResponse toRequirement(RequirementDTO entry, LocalDate asOf) {
    return new RequirementResponse(
        entry.requirement(),
        entry.optional(),
        entry.satisfied(),
        entry.state(),
        isExpiringSoon(entry, asOf),
        toSubmission(entry.current()),
        toSubmission(entry.pending()));
  }

  static boolean isExpiringSoon(RequirementDTO entry, LocalDate asOf) {
    LocalDate expiresOn = entry.current() == null ? null : entry.current().expiresOn();
    return entry.state() == ComplianceState.APPROVED
        && expiresOn != null
        && !expiresOn.isAfter(asOf.plusDays(DriverComplianceView.EXPIRY_WINDOW_DAYS));
  }

  SubmissionResponse toSubmission(DocumentSubmission submission) {
    if (submission == null) {
      return null;
    }
    DocumentReview review = submission.getReview();
    MediaFileModel file = submission.getReviewedFile();
    DriverCnhModel cnh = submission instanceof DriverCnhModel license ? license : null;
    return new SubmissionResponse(
        submission.kind(),
        submission.getToken(),
        cnh == null ? submission.number() : null,
        submission.expiresOn(),
        review.getStatus(),
        review.getRejectionReasonCode(),
        review.getRejectionReason(),
        review.getReviewedAt(),
        submission.getCreatedAt(),
        submission.getVersion(),
        DriverComplianceDetailMapper.fileUrlOf(submission, file),
        DocumentFileMapper.toResponse(file),
        cnh == null ? null : cnh.getCategory(),
        cnh == null ? null : cnh.getIssueDate(),
        cnh == null ? null : cnh.getRegistrationNumber());
  }
}
