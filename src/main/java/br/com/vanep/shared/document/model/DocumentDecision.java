package br.com.vanep.shared.document.model;

import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.ReviewMethod;

public record DocumentDecision(
    DocumentDecisionStatus status,
    ReviewMethod reviewMethod,
    String externalCheckId,
    DocumentRejectionReason rejectionReasonCode,
    String rejectionReason) {}
