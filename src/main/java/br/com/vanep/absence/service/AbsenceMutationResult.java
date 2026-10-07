package br.com.vanep.absence.service;

import br.com.vanep.absence.model.AbsenceModel;
import java.util.List;

public record AbsenceMutationResult(List<AbsenceModel> absences, boolean createdAny) {}
