package br.com.vanep.clientday.dto;

import br.com.vanep.shared.enums.OperationShift;
import java.time.LocalTime;
import java.util.List;

/**
 * One van the client's children ride today: a driver and a shift. The vehicle is the driver's main
 * active vehicle, because contracts are not tied to a vehicle; {@code vehicleModel} and {@code
 * plate} are null when the driver has none. Times are local to the service day:
 *
 * <ul>
 *   <li>{@code departureTime}: first pickup of the driver's whole route in the shift;
 *   <li>{@code boardingStart}/{@code boardingEnd}: first and last home pickup of the client's
 *       children (null when they only ride back today);
 *   <li>{@code schoolDeparture}: first school pickup of the return leg (null without a return).
 * </ul>
 */
public record ClientDayVanDTO(
    String driverToken,
    String driverName,
    String vehicleModel,
    String plate,
    OperationShift shift,
    LocalTime departureTime,
    List<ClientDayRiderDTO> riders,
    LocalTime boardingStart,
    LocalTime boardingEnd,
    LocalTime schoolDeparture) {}
