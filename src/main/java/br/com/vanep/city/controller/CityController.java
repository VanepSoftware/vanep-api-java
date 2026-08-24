package br.com.vanep.city.controller;

import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.service.CityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cities")
/**
 * Leitura de cidades. A escrita saiu: a fonte da árvore geográfica passou a ser o Google Places, e
 * cidade nasce sob demanda pelo resolver. Um CRUD manual em paralelo criaria linhas que nunca
 * casariam com as resolvidas, que é o R2 por outro caminho.
 */
public class CityController {

  private final CityService service;

  public CityController(CityService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_cities')")
  public Page<CityResponseDTO> list(
      @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_city')")
  public CityResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }
}
