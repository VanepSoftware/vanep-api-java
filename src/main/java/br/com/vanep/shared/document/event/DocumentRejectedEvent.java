package br.com.vanep.shared.document.event;

import br.com.vanep.shared.document.enums.DocumentKind;

public record DocumentRejectedEvent(DocumentKind kind, String token) {}
