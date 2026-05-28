package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordHasherTest {

  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private PasswordHasher passwordHasher;

  @Test
  void encode_delegatesToEncoder() {
    when(passwordEncoder.encode("raw")).thenReturn("ENC");
    assertThat(passwordHasher.encode("raw")).isEqualTo("ENC");
    verify(passwordEncoder).encode("raw");
  }

  @Test
  void matches_delegatesToEncoder() {
    when(passwordEncoder.matches("raw", "ENC")).thenReturn(true);
    assertThat(passwordHasher.matches("raw", "ENC")).isTrue();
  }
}
