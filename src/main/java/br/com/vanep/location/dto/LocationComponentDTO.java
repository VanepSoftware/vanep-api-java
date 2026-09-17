package br.com.vanep.location.dto;

import br.com.vanep.location.enums.LocationLevel;

public record LocationComponentDTO(
    LocationLevel level, int depth, String name, String shortName, String sourceType) {}
