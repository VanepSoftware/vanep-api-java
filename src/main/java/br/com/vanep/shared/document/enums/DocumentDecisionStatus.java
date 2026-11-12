package br.com.vanep.shared.document.enums;

public enum DocumentDecisionStatus {
  APPROVED(DocumentStatus.APPROVED),
  REJECTED(DocumentStatus.REJECTED);

  private final DocumentStatus status;

  DocumentDecisionStatus(DocumentStatus status) {
    this.status = status;
  }

  public DocumentStatus toStatus() {
    return status;
  }
}
