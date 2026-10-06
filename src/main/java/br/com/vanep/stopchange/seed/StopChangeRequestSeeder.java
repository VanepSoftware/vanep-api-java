package br.com.vanep.stopchange.seed;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.stopchange.enums.StopChangeStatus;
import br.com.vanep.stopchange.model.StopChangeRequestModel;
import br.com.vanep.stopchange.repository.StopChangeRequestRepository;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.model.UserModel;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StopChangeRequestSeeder {

  private static final Logger log = LoggerFactory.getLogger(StopChangeRequestSeeder.class);

  private final StopChangeRequestRepository stopChangeRequests;
  private final TripRepository trips;
  private final DependentRepository dependents;
  private final ClientRepository clients;
  private final AddressRepository addresses;
  private final CityRepository cities;

  public StopChangeRequestSeeder(
      StopChangeRequestRepository stopChangeRequests,
      TripRepository trips,
      DependentRepository dependents,
      ClientRepository clients,
      AddressRepository addresses,
      CityRepository cities) {
    this.stopChangeRequests = stopChangeRequests;
    this.trips = trips;
    this.dependents = dependents;
    this.clients = clients;
    this.addresses = addresses;
    this.cities = cities;
  }

  public void seed() {
    if (stopChangeRequests.count() > 0) {
      return;
    }

    List<TripModel> tripList = trips.findAll();
    if (tripList.isEmpty()) {
      log.info("Seed: stop change request seed skipped; no trips available.");
      return;
    }

    List<DependentModel> dependentList = dependents.findAll();
    if (dependentList.isEmpty()) {
      log.info("Seed: stop change request seed skipped; no dependents available.");
      return;
    }

    DependentModel dependent = dependentList.get(0);
    ClientModel client = clients.findById(dependent.getClientId()).orElse(null);
    if (client == null) {
      log.info("Seed: stop change request seed skipped; client not found for dependent.");
      return;
    }

    UserModel requesterUser = client.getUser();
    if (requesterUser == null) {
      log.info("Seed: stop change request seed skipped; requester user not found.");
      return;
    }

    TripModel trip =
        tripList.stream()
            .filter(t -> t.getServiceDate().equals(LocalDate.now()))
            .findFirst()
            .orElse(tripList.get(0));

    AddressModel address =
        addresses.findAll().stream()
            .findFirst()
            .orElseGet(
                () -> {
                  CityModel city = cities.findAll().stream().findFirst().orElse(null);
                  if (city == null) {
                    return null;
                  }
                  AddressModel addr = new AddressModel();
                  addr.setCity(city);
                  addr.setStreet("Avenida Paulista");
                  addr.setNumber("1000");
                  addr.setZipCode("01310100");
                  addr.setActive(true);
                  return addresses.save(addr);
                });

    if (address == null) {
      log.info("Seed: stop change request seed skipped; cannot create dropoff address.");
      return;
    }

    StopChangeRequestModel request = new StopChangeRequestModel();
    request.setDependent(dependent);
    request.setTrip(trip);
    request.setServiceDate(trip.getServiceDate());
    request.setRequestedByUser(requesterUser);
    request.setNewDropoffAddress(address);
    request.setReason("Desembarque na casa da avó hoje");
    request.setStatus(StopChangeStatus.PENDING);

    stopChangeRequests.save(request);
    log.info("Seed: stop change request created (token={}).", request.getToken());
  }
}
