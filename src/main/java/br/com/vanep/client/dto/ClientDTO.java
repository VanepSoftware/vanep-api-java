package br.com.vanep.client.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import br.com.vanep.client.model.ClientModel;

public class ClientDTO {

  public record Request(
      @NotNull(message = "A foto é obrigatória") 
      String photo
  ){}

  public record Response(
      String token,
      String userToken,
      String photo,
      BigDecimal rating,
      Long addressId,
      boolean active,
      Instant createdAt,
      Instant updatedAt
  ) {
    public Response(ClientModel client) {
      this(
          client.getToken(),
          client.getUser().getToken(),
          client.getPhoto(),
          client.getRating(),
          client.getAddressId(),
          client.isActive(),
          client.getCreatedAt(),
          client.getUpdatedAt()
      );
    }
  }
}