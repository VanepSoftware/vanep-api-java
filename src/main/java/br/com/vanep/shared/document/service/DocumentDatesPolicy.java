package br.com.vanep.shared.document.service;

import br.com.vanep.shared.error.ApiProblemException;
import java.time.LocalDate;

public final class DocumentDatesPolicy {

  private DocumentDatesPolicy() {}

  public static void requireExpiryWithin(
      String field, LocalDate expiresAt, LocalDate latestExpiry, LocalDate today) {
    if (expiresAt == null) {
      return;
    }
    if (expiresAt.isBefore(today)) {
      throw ApiProblemException.badRequest("document.expires_at.past").withProperty("field", field);
    }
    if (expiresAt.isAfter(latestExpiry)) {
      throw ApiProblemException.badRequest("document.expires_at.too_far")
          .withProperty("field", field);
    }
  }
}
