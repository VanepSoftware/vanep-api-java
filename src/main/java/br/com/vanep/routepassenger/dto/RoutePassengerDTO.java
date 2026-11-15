package br.com.vanep.routepassenger.dto;

import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.LocalTime;

public record RoutePassengerDTO(
    PassengerSource source,
    String token,
    String name,
    String dependentToken,
    String schoolToken,
    String schoolName,
    RouteLeg leg,
    LocalTime pickupTime,
    String pickupZipCode,
    String pickupStreet,
    String pickupNumber,
    String pickupComplement,
    String pickupNeighborhood,
    String pickupCityToken,
    String pickupCityName,
    String pickupGooglePlaceId) {}
