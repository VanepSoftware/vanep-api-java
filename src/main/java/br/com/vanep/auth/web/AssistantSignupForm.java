package br.com.vanep.auth.web;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssistantSignupForm extends AccountSignupForm {

  private String linkCode;
}
