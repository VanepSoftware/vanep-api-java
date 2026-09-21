package br.com.vanep.cep.client;

import br.com.vanep.cep.dto.ViaCepResponseDTO;
import br.com.vanep.cep.exception.ViaCepLookupException;
import br.com.vanep.cep.exception.ViaCepNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ViaCepClient {
  private final RestClient restClient;

  public ViaCepClient(@Qualifier("viaCepRestClient") RestClient viaCepRestClient) {
    this.restClient = viaCepRestClient;
  }

  public ViaCepResponseDTO findByCep(String cep) {
    try {
      ViaCepResponseDTO response =
          restClient
              .get()
              .uri("/ws/{cep}/json/", cep)
              .retrieve()
              .onStatus(
                  status -> status.isError(),
                  (request, clientResponse) -> {
                    throw new ViaCepLookupException(
                        "ViaCEP responded " + clientResponse.getStatusCode() + ".");
                  })
              .toEntity(ViaCepResponseDTO.class)
              .getBody();

      if (response == null || response.isError()) {
        throw new ViaCepNotFoundException(cep);
      }
      return response;
    } catch (ViaCepNotFoundException | ViaCepLookupException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      throw new ViaCepLookupException("Failed to reach ViaCEP.", ex);
    } catch (RuntimeException ex) {
      throw new ViaCepLookupException("Unexpected ViaCEP response.", ex);
    }
  }
}
