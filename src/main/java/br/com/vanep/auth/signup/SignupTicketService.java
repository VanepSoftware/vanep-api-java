package br.com.vanep.auth.signup;

import br.com.vanep.auth.signup.model.SignupTicketModel;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.user.enums.AuthProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupTicketService {

  private final SignupTicketRepository tickets;
  private final Duration ttl;

  public SignupTicketService(
      SignupTicketRepository tickets,
      @Value("${vanep.auth.signup-ticket.ttl-minutes:15}") long ttlMinutes) {
    this.tickets = tickets;
    this.ttl = Duration.ofMinutes(ttlMinutes);
  }

  /** Returns the raw ticket, which is never stored: only its hash is. */
  @Transactional
  public String issue(AuthProvider provider, String providerUid, String email, String name) {
    String rawTicket = SecureTokens.generate();
    SignupTicketModel ticket = new SignupTicketModel();
    ticket.setTicketHash(SecureTokens.hash(rawTicket));
    ticket.setProvider(provider);
    ticket.setProviderUid(providerUid);
    ticket.setEmail(email);
    ticket.setName(name);
    ticket.setExpiresAt(Instant.now().plus(ttl));
    tickets.save(ticket);
    return rawTicket;
  }

  @Transactional
  public Optional<SignupTicketModel> consume(String rawTicket) {
    if (rawTicket == null || rawTicket.isBlank()) {
      return Optional.empty();
    }
    Instant now = Instant.now();
    return tickets
        .lockByTicketHash(SecureTokens.hash(rawTicket))
        .filter(ticket -> ticket.getConsumedAt() == null)
        .filter(ticket -> ticket.getExpiresAt().isAfter(now))
        .map(
            ticket -> {
              ticket.setConsumedAt(now);
              return tickets.save(ticket);
            });
  }
}
