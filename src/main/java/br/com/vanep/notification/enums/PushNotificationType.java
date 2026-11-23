package br.com.vanep.notification.enums;

import java.util.Locale;

public enum PushNotificationType {
  CHAT_MESSAGE,
  PROPOSAL_RECEIVED,
  PROPOSAL_ACCEPTED,
  PROPOSAL_DECLINED,
  CONTRACT_CREATED,
  DRIVER_APPROVED,
  DRIVER_REJECTED;

  public String titleKey() {
    return "push." + name().toLowerCase(Locale.ROOT) + ".title";
  }

  public String bodyKey() {
    return "push." + name().toLowerCase(Locale.ROOT) + ".body";
  }
}
