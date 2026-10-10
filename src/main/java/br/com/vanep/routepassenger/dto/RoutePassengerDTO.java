package br.com.vanep.routepassenger.dto;

import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.LocalTime;

public record RoutePassengerDTO(
    PassengerSource source,
    String token,
    String name,
    String schoolToken,
    String schoolName,
    RouteLeg leg,
    LocalTime windowStart,
    LocalTime windowEnd,
    String pickupZipCode,
    String pickupStreet,
    String pickupNumber,
    String pickupComplement,
    String pickupNeighborhood,
    String pickupDistrictName,
    String pickupCityToken,
    String pickupCityName,
    String pickupGooglePlaceId) {}
