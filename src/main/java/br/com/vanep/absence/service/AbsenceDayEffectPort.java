package br.com.vanep.absence.service;

import br.com.vanep.absence.model.AbsenceModel;

public interface AbsenceDayEffectPort {
  void onAbsenceRecorded(AbsenceModel absence);
}
