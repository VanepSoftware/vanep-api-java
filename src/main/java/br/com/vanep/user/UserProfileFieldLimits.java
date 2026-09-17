package br.com.vanep.user;

/** Max lengths for editable profile fields — mirrors {@code users} column sizes (V1 / V17). */
public final class UserProfileFieldLimits {

  public static final int NAME_MAX = 255;
  public static final int PHONE_MAX = 32;
  public static final int EMAIL_MAX = 255;

  private UserProfileFieldLimits() {}
}
