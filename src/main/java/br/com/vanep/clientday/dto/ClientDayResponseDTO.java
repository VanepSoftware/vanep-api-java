package br.com.vanep.clientday.dto;

import java.time.LocalDate;
import java.util.List;

/** What the client's children have today with their vans, for the home screen. */
public record ClientDayResponseDTO(LocalDate date, List<ClientDayVanDTO> vans) {}
