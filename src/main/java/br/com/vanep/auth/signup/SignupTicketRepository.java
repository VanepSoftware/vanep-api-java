package br.com.vanep.auth.signup;

import br.com.vanep.auth.signup.model.SignupTicketModel;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignupTicketRepository extends JpaRepository<SignupTicketModel, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from SignupTicketModel t where t.ticketHash = :ticketHash")
  Optional<SignupTicketModel> lockByTicketHash(@Param("ticketHash") String ticketHash);
}
