package br.com.vanep.clientday.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One of the next school days of the client: which legs at least one child rides. {@code
 * firstPickup} is the earliest pickup of the client's children that day, null when none rides.
 */
public record ClientDayUpcomingDTO(
    LocalDate date, boolean going, boolean returning, LocalTime firstPickup) {}
