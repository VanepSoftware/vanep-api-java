package br.com.vanep.dependent.resolver;

import java.util.Optional;

public interface AddressTokenResolver {

  Optional<Long> resolveId(String token);

  Optional<String> resolveToken(Long id);
}
