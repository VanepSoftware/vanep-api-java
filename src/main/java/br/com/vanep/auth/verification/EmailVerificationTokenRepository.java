package br.com.vanep.auth.verification;

import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationTokenModel, Long> {

  Optional<EmailVerificationTokenModel> findByTokenHash(String tokenHash);
}
