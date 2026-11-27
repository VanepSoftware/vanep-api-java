package br.com.vanep.clientday.dto;

/** A child of the client who has no van today because no ACTIVE contract covers them. */
public record ClientDayChildDTO(String dependentToken, String name) {}
