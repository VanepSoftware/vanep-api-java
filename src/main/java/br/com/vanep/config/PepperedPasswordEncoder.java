package br.com.vanep.config;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

public class PepperedPasswordEncoder implements PasswordEncoder {

  private final PasswordEncoder delegate;
  private final String pepper;

  public PepperedPasswordEncoder(PasswordEncoder delegate, String pepper) {
    this.delegate = delegate;
    this.pepper = pepper != null ? pepper : "";
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return delegate.encode(pepper(rawPassword));
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    return delegate.matches(pepper(rawPassword), encodedPassword);
  }

  @Override
  public boolean upgradeEncoding(String encodedPassword) {
    return delegate.upgradeEncoding(encodedPassword);
  }

  private String pepper(CharSequence rawPassword) {
    if (!StringUtils.hasText(pepper)) {
      return rawPassword.toString();
    }
    return pepper + rawPassword;
  }
}
