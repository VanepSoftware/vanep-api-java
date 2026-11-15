package br.com.vanep.dependent.repository;

import br.com.vanep.dependent.model.DependentSchoolDayModel;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DependentSchoolDayRepository extends JpaRepository<DependentSchoolDayModel, Long> {

  List<DependentSchoolDayModel> findByDependentId(Long dependentId);

  List<DependentSchoolDayModel> findByDependentIdIn(Collection<Long> dependentIds);
}
