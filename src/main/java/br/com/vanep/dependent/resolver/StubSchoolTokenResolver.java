package br.com.vanep.dependent.resolver;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StubSchoolTokenResolver implements SchoolTokenResolver {

  @Override
  public Optional<Long> resolveId(String token) {
    if (!StringUtils.hasText(token)) {
      return Optional.empty();
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Escola não encontrada.");
  }

  @Override
  public Optional<String> resolveToken(Long id) {
    return Optional.empty();
  }
}
