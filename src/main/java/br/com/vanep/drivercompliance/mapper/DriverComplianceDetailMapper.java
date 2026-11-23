package br.com.vanep.drivercompliance.mapper;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.mapper.DriverCnhMapper;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO.RequirementDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO.DecisionResponse;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO.DriverResponse;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO.LastDecisionResponse;
import br.com.vanep.drivercompliance.dto.DriverComplianceDetailResponseDTO.RequirementResponse;
import br.com.vanep.drivercompliance.dto.SubmissionResponseDTO;
import br.com.vanep.drivercompliance.dto.SubmissionResponseDTO.ActorResponseDTO;
import br.com.vanep.driverdocument.mapper.DriverDocumentMapper;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.shared.document.mapper.DocumentFileMapper;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.vehicledocument.mapper.VehicleDocumentMapper;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class DriverComplianceDetailMapper {

  public DriverResponse toDriver(DriverModel driver, @Nullable String city) {
    UserModel user = driver.getUser();
    return new DriverResponse(
        driver.getToken(),
        user.getName(),
        user.getEmail(),
        user.getPhone(),
        user.getDocument(),
        user.getBirthDate(),
        city,
        driver.getExperienceYears(),
        driver.getCreatedAt(),
        driver.getApprovalStatus(),
        driver.getSubmittedAt(),
        driver.getRejectionReasonCode(),
        driver.getRejectionReason(),
        driver.getReviewedAt(),
        toActor(driver.getReviewedBy()));
  }

  public DecisionResponse toDecision(DriverComplianceDTO compliance) {
    return new DecisionResponse(
        compliance.canApprove(),
        compliance.nextAction(),
        compliance.pendingReviewCount(),
        compliance.blockers());
  }

  public RequirementResponse toRequirement(
      RequirementDTO entry,
      @Nullable DocumentSubmission lastDecided,
      Map<Long, UserModel> correctors) {
    return new RequirementResponse(
        entry.requirement(),
        entry.optional(),
        entry.satisfied(),
        entry.state(),
        toSubmission(entry.current(), correctors),
        toSubmission(entry.pending(), correctors),
        toLastDecision(lastDecided));
  }

  public @Nullable SubmissionResponseDTO toSubmission(
      @Nullable DocumentSubmission submission, Map<Long, UserModel> correctors) {
    if (submission == null) {
      return null;
    }
    DocumentReview review = submission.getReview();
    MediaFileModel file = submission.getReviewedFile();
    DriverCnhModel cnh = submission instanceof DriverCnhModel license ? license : null;
    return new SubmissionResponseDTO(
        submission.kind(),
        submission.getToken(),
        cnh == null ? submission.number() : null,
        submission.expiresOn(),
        review.getStatus(),
        review.getReviewMethod(),
        review.getRejectionReasonCode(),
        review.getRejectionReason(),
        toActor(review.getReviewedBy()),
        review.getReviewedAt(),
        review.getDataCorrectedAt(),
        toActor(correctorOf(review, correctors)),
        submission.getCreatedAt(),
        submission.getVersion(),
        fileUrlOf(submission, file),
        DocumentFileMapper.toResponse(file),
        cnh == null ? null : cnh.getCategory(),
        cnh == null ? null : cnh.getIssueDate(),
        cnh == null ? null : cnh.getRegistrationNumber(),
        cnh == null ? null : cnh.getFirstLicenseDate(),
        cnh == null ? null : cnh.getSecurityNumber(),
        cnh == null ? null : cnh.getIssuingState());
  }

  @Nullable LastDecisionResponse toLastDecision(@Nullable DocumentSubmission superseded) {
    if (superseded == null) {
      return null;
    }
    DocumentReview review = superseded.getReview();
    return new LastDecisionResponse(
        review.getStatus(),
        review.getRejectionReasonCode(),
        review.getRejectionReason(),
        toActor(review.getReviewedBy()),
        review.getReviewedAt(),
        superseded.getSupersededAt());
  }

  public static @Nullable UserModel correctorOf(
      DocumentReview review, Map<Long, UserModel> correctors) {
    return review.getDataCorrectedById() == null
        ? null
        : correctors.get(review.getDataCorrectedById());
  }

  public static @Nullable ActorResponseDTO toActor(@Nullable UserModel user) {
    return user == null ? null : new ActorResponseDTO(user.getToken(), user.getName());
  }

  static @Nullable String fileUrlOf(DocumentSubmission submission, @Nullable MediaFileModel file) {
    String basePath =
        switch (submission.kind()) {
          case DRIVER_CNH -> DriverCnhMapper.BASE_PATH;
          case DRIVER_DOCUMENT -> DriverDocumentMapper.BASE_PATH;
          case VEHICLE_DOCUMENT -> VehicleDocumentMapper.BASE_PATH;
        };
    return MediaUrl.of(basePath, submission.getToken(), "file", file);
  }
}
