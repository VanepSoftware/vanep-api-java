package br.com.vanep.absence.controller;

import br.com.vanep.absence.dto.AbsenceClientRequestDTO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client-drivers/{linkToken}/absences")
public class AbsenceController {

  private final AbsenceService absences;
  private final AbsenceMapper mapper;

  public AbsenceController(AbsenceService absences, AbsenceMapper mapper) {
    this.absences = absences;
    this.mapper = mapper;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('report_absence')")
  public ResponseEntity<List<AbsenceResponseDTO>> report(
      @PathVariable String linkToken,
      @Valid @RequestBody AbsenceClientRequestDTO request,
      Authentication authentication) {
    AbsenceMutationResult result =
        absences.reportForClient(
            SecurityHelper.requireCallerUid(authentication),
            linkToken,
            request.dependentToken(),
            request.scope());
    HttpStatus status = result.createdAny() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(mapper.toResponses(result.absences()));
  }

  @GetMapping("/today")
  @PreAuthorize("hasAuthority('report_absence')")
  public List<AbsenceResponseDTO> today(
      @PathVariable String linkToken,
      @RequestParam String dependentToken,
      Authentication authentication) {
    return mapper.toResponses(
        absences.listTodayForClient(
            SecurityHelper.requireCallerUid(authentication), linkToken, dependentToken));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('report_absence')")
  public void undo(
      @PathVariable String linkToken,
      @Valid @RequestBody AbsenceClientRequestDTO request,
      Authentication authentication) {
    absences.undoForClient(
        SecurityHelper.requireCallerUid(authentication),
        linkToken,
        request.dependentToken(),
        request.scope());
  }
}
