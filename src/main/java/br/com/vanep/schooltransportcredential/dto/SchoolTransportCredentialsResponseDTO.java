package br.com.vanep.schooltransportcredential.dto;

import java.time.LocalDate;
import java.util.List;

public record SchoolTransportCredentialsResponseDTO(
    String vehicleToken,
    LocalDate date,
    boolean satisfied,
    List<SchoolTransportRequirementResponseDTO> requirements) {}
