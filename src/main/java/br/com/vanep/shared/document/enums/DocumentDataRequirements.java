package br.com.vanep.shared.document.enums;

import java.time.LocalDate;

public interface DocumentDataRequirements {

  boolean requiresNumberOnApproval();

  boolean requiresExpiryOnApproval();

  LocalDate latestExpiryOn(LocalDate today);
}
