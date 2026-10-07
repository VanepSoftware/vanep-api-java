package br.com.vanep.absence.controller;

import br.com.vanep.absence.dto.AbsenceNoShowRequestDTO;
import br.com.vanep.absence.dto.AbsenceResponseDTO;
import br.com.vanep.absence.mapper.AbsenceMapper;
import br.com.vanep.absence.service.AbsenceMutationResult;
import br.com.vanep.absence.service.AbsenceService;
import br.com.vanep.auth.security.SecurityHelper;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripToken}/no-shows")
public class AbsenceNoShowController {

  private final AbsenceService absences;
  private final AbsenceMapper mapper;

  public AbsenceNoShowController(AbsenceService absences, AbsenceMapper mapper) {
    this.absences = absences;
    this.mapper = mapper;
  }

  @PostMapping
  @PreAuthorize(
      "hasAuthority('report_no_show') and @sec.isTripOperator(#tripToken, authentication)")
  public ResponseEntity<List<AbsenceResponseDTO>> report(
      @PathVariable String tripToken,
      @Valid @RequestBody AbsenceNoShowRequestDTO request,
      Authentication authentication) {
    AbsenceMutationResult result =
        absences.reportNoShow(
            SecurityHelper.requireCallerUid(authentication),
            tripToken,
            request.dependentToken(),
            request.reason());
    HttpStatus status = result.createdAny() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(mapper.toResponses(result.absences()));
  }
}
