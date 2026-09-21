package br.com.vanep.media.service;

import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.enums.MediaOwnerType;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MediaOwnerResolver {

  private final DriverRepository drivers;
  private final ClientRepository clients;
  private final AssistantRepository assistants;
  private final VehicleRepository vehicles;
  private final DriverCnhRepository cnhs;
  private final DriverDocumentRepository documents;

  public MediaOwnerResolver(
      DriverRepository drivers,
      ClientRepository clients,
      AssistantRepository assistants,
      VehicleRepository vehicles,
      DriverCnhRepository cnhs,
      DriverDocumentRepository documents) {
    this.drivers = drivers;
    this.clients = clients;
    this.assistants = assistants;
    this.vehicles = vehicles;
    this.cnhs = cnhs;
    this.documents = documents;
  }

  public Optional<Long> resolveOwnerId(MediaOwnerType ownerType, String ownerToken) {
    return switch (ownerType) {
      case DRIVER -> drivers.findByToken(ownerToken).map(driver -> driver.getId());
      case CLIENT -> clients.findByToken(ownerToken).map(client -> client.getId());
      case ASSISTANT -> assistants.findByToken(ownerToken).map(assistant -> assistant.getId());
      case VEHICLE -> vehicles.findByToken(ownerToken).map(vehicle -> vehicle.getId());
      case DRIVER_CNH -> cnhs.findByToken(ownerToken).map(cnh -> cnh.getId());
      case DRIVER_DOCUMENT -> documents.findByToken(ownerToken).map(document -> document.getId());
    };
  }

  public Optional<String> resolveOwnerUserToken(MediaOwnerType ownerType, Long ownerId) {
    return switch (ownerType) {
      case DRIVER -> drivers.findById(ownerId).map(driver -> driver.getUser().getToken());
      case CLIENT -> clients.findById(ownerId).map(client -> client.getUser().getToken());
      case ASSISTANT ->
          assistants.findById(ownerId).map(assistant -> assistant.getUser().getToken());
      case VEHICLE ->
          vehicles.findById(ownerId).map(vehicle -> vehicle.getDriver().getUser().getToken());
      case DRIVER_CNH -> cnhs.findById(ownerId).map(cnh -> cnh.getDriver().getUser().getToken());
      case DRIVER_DOCUMENT ->
          documents.findById(ownerId).map(document -> document.getDriver().getUser().getToken());
    };
  }
}
