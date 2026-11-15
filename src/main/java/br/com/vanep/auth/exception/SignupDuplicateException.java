package br.com.vanep.auth.exception;

import br.com.vanep.auth.enums.AuthErrorCode;
import java.io.Serial;
import java.util.List;
import lombok.Getter;

/** Carries every duplicated field at once, so the user can fix them all in one go. */
@Getter
public class SignupDuplicateException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient List<DuplicateField> duplicates;

  public SignupDuplicateException(List<DuplicateField> duplicates) {
    super(duplicates.getFirst().messageKey());
    this.duplicates = List.copyOf(duplicates);
  }

  /** The first duplicate's code, kept as the envelope code for clients that read only that. */
  public AuthErrorCode getCode() {
    return duplicates.getFirst().code();
  }

  public record DuplicateField(String field, String messageKey, AuthErrorCode code) {}
}
