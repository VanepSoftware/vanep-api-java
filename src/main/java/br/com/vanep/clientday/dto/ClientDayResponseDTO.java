package br.com.vanep.clientday.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * What the client's children have today with their vans, for the home screen. {@code upcoming} is
 * only filled when no child rides today and some child rides in the next two weeks. {@code
 * childrenWithoutVan} are the dependents no ACTIVE contract covers today. {@code
 * expiredInspections} lists the drivers of the contracts in force whose van inspection expired.
 */
public record ClientDayResponseDTO(
    LocalDate date,
    List<ClientDayVanDTO> vans,
    List<ClientDayUpcomingDTO> upcoming,
    List<ClientDayChildDTO> childrenWithoutVan,
    List<ClientDayExpiredInspectionDTO> expiredInspections) {}
