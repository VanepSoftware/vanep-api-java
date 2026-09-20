package br.com.vanep.auth.signup;

import br.com.vanep.auth.dto.GoogleSignupCompleteRequestDTO;
import br.com.vanep.auth.exception.InvalidSignupTicketException;
import br.com.vanep.auth.oauth.OAuthAccountService;
import br.com.vanep.auth.signup.model.SignupTicketModel;
import br.com.vanep.auth.validation.CpfValidator;
import br.com.vanep.auth.web.RegistrationService;
import br.com.vanep.user.model.UserModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleSignupService {

  private final SignupTicketService signupTickets;
  private final OAuthAccountService accounts;
  private final RegistrationService registrationService;

  public GoogleSignupService(
      SignupTicketService signupTickets,
      OAuthAccountService accounts,
      RegistrationService registrationService) {
    this.signupTickets = signupTickets;
    this.accounts = accounts;
    this.registrationService = registrationService;
  }

  /** One transaction, so a duplicate found here gives the ticket back unused. */
  @Transactional
  public UserModel completeRegistration(GoogleSignupCompleteRequestDTO request) {
    SignupTicketModel ticket =
        signupTickets
            .consume(request.getSignupTicket())
            .orElseThrow(InvalidSignupTicketException::new);

    request.setDocument(CpfValidator.normalize(request.getDocument()));
    registrationService.rejectDuplicates(ticket.getEmail(), request.getDocument());

    return accounts.completeRegistration(
        ticket.getProvider(),
        ticket.getProviderUid(),
        ticket.getEmail(),
        ticket.getName(),
        request);
  }
}
