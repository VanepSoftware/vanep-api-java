package br.com.vanep.absence.service;

import br.com.vanep.absence.model.AbsenceModel;
import org.springframework.stereotype.Component;

@Component
public class NoOpAbsenceCounterpartNotifier implements AbsenceCounterpartNotifier {

  @Override
  public void notifyCounterpart(AbsenceModel absence) {}
}
