package br.com.vanep.familyinvite.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class FamilyInviteCodeGenerator {

  static final int CODE_LENGTH = 12;

  // No 0/O, 1/I/L: the code is typed or read aloud from a group chat as often as tapped.
  static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

  private final SecureRandom random = new SecureRandom();

  public String generateCode() {
    StringBuilder code = new StringBuilder(CODE_LENGTH);
    for (int position = 0; position < CODE_LENGTH; position++) {
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }
}
