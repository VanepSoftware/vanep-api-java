package br.com.vanep.clientday.dto;

/** A child of the client who rides this van today. */
public record ClientDayRiderDTO(String dependentToken, String name) {}
