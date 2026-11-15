package br.com.vanep.familyinvite;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StudentFamilyFixtures {

  private final UserRepository users;
  private final DriverRepository drivers;
  private final ClientRepository clients;
  private final DependentRepository dependents;
  private final SchoolRepository schools;
  private final AddressRepository addresses;
  private final CityRepository cities;
  private final StateRepository states;
  private final CountryRepository countries;
  private final VehicleRepository vehicles;

  public StudentFamilyFixtures(
      UserRepository users,
      DriverRepository drivers,
      ClientRepository clients,
      DependentRepository dependents,
      SchoolRepository schools,
      AddressRepository addresses,
      CityRepository cities,
      StateRepository states,
      CountryRepository countries,
      VehicleRepository vehicles) {
    this.users = users;
    this.drivers = drivers;
    this.clients = clients;
    this.dependents = dependents;
    this.schools = schools;
    this.addresses = addresses;
    this.cities = cities;
    this.states = states;
    this.countries = countries;
    this.vehicles = vehicles;
  }

  public CityModel createCity() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country.setLocale("pt-BR");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    state = states.save(state);

    CityModel city = new CityModel();
    city.setState(state);
    city.setName("Campinas");
    return cities.save(city);
  }

  public SchoolModel createSchool(CityModel city, String name) {
    SchoolModel school = new SchoolModel();
    school.setName(name);
    school.setCity(city);
    return schools.save(school);
  }

  public DriverModel createDriver(String name, String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, name, email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    return drivers.save(driver);
  }

  public ClientModel createClient(String name, String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, name, email, document));
    return clients.save(client);
  }

  public DependentModel createDependent(
      ClientModel client, String name, SchoolModel school, CityModel city, LocalDate birthDate) {
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet("Rua A");
    address.setNumber("10");
    DependentModel dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName(name);
    dependent.setSchoolId(school.getId());
    dependent.setAddressId(addresses.save(address).getId());
    dependent.setBirthDate(birthDate);
    return dependents.save(dependent);
  }

  public VehicleModel createVehicle(DriverModel driver, String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(plate);
    vehicle.setBrand("Mercedes");
    vehicle.setModel("Sprinter");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    return vehicles.save(vehicle);
  }

  public Map<String, Object> passengerRequest(
      String name, SchoolModel school, CityModel city, String vehicleToken) {
    Map<String, Object> request = new HashMap<>();
    request.put("name", name);
    request.put("schoolToken", school.getToken());
    request.put("schoolShift", "MORNING");
    request.put(
        "address",
        Map.of(
            "cityToken", city.getToken(),
            "street", "Rua das Flores",
            "zipCode", "13015904",
            "number", "100"));
    request.put(
        "slots",
        List.of(
            Map.of(
                "weekday", "MONDAY", "leg", "OUTBOUND", "shift", "MORNING", "pickupTime", "06:40"),
            Map.of(
                "weekday",
                "MONDAY",
                "leg",
                "RETURN",
                "shift",
                "AFTERNOON",
                "pickupTime",
                "12:10")));
    if (vehicleToken != null) {
      request.put("vehicleToken", vehicleToken);
    }
    return request;
  }

  UserModel createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }
}
