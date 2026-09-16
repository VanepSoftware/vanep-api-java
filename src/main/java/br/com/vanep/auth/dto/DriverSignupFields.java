package br.com.vanep.auth.dto;

import java.math.BigDecimal;

/** Driver data every sign-up channel carries, so the role record is created the same way. */
public interface DriverSignupFields {

  String getCnpj();

  Integer getExperienceYears();

  BigDecimal getBasePrice();
}
