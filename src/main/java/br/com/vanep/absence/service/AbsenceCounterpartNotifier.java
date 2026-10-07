package br.com.vanep.absence.service;

import br.com.vanep.absence.model.AbsenceModel;

public interface AbsenceCounterpartNotifier {
  void notifyCounterpart(AbsenceModel absence);
}
