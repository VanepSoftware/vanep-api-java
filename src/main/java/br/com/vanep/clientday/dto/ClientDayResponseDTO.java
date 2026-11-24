package br.com.vanep.clientday.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * What the client's children have today with their vans, for the home screen. {@code upcoming} is
 * only filled when no child rides today and some child rides in the next two weeks.
 */
public record ClientDayResponseDTO(
    LocalDate date, List<ClientDayVanDTO> vans, List<ClientDayUpcomingDTO> upcoming) {}
