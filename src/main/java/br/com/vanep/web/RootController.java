package br.com.vanep.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identificação mínima da API (liveness / smoke). Mantido fora de {@code *.controller} para não
 * receber o prefixo {@code /api} de {@link br.com.vanep.config.ApiWebConfig}.
 */
@RestController
public class RootController {

  @GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
  public String root() {
    return "vanep";
  }
}
