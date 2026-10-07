package br.com.vanep.absence.service;

import br.com.vanep.absence.model.AbsenceModel;
import org.springframework.stereotype.Component;

@Component
public class NoOpAbsenceDayEffectPort implements AbsenceDayEffectPort {

  @Override
  public void onAbsenceRecorded(AbsenceModel absence) {}
}
