package br.com.vanep.auth.dto;

import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.enums.UserType;
import java.time.LocalDate;

/** What completing a social sign-up needs, whether it arrives from the web form or the API. */
public interface SignupCompletionFields extends DriverSignupFields {

  UserType getType();

  String getDocument();

  String getPhone();

  LocalDate getBirthDate();

  Gender getGender();
}
