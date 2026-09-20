package br.com.vanep.auth.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.signup.model.SignupTicketModel;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.user.enums.AuthProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignupTicketServiceTest {

  @Mock private SignupTicketRepository tickets;

  private SignupTicketService service() {
    return new SignupTicketService(tickets, 15);
  }

  @Test
  void issueStoresOnlyTheHashAndTheConfiguredTtl() {
    SignupTicketService service = service();
    when(tickets.save(any(SignupTicketModel.class))).thenAnswer(inv -> inv.getArgument(0));

    String rawTicket =
        service.issue(AuthProvider.GOOGLE, "google-subject-1", "person@gmail.com", "Person");

    ArgumentCaptor<SignupTicketModel> saved = ArgumentCaptor.forClass(SignupTicketModel.class);
    org.mockito.Mockito.verify(tickets).save(saved.capture());
    SignupTicketModel ticket = saved.getValue();
    assertThat(rawTicket).isNotBlank();
    assertThat(ticket.getTicketHash())
        .isEqualTo(SecureTokens.hash(rawTicket))
        .isNotEqualTo(rawTicket);
    assertThat(ticket.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(ticket.getProviderUid()).isEqualTo("google-subject-1");
    assertThat(ticket.getEmail()).isEqualTo("person@gmail.com");
    assertThat(ticket.getExpiresAt())
        .isBetween(
            Instant.now().plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(16)));
  }

  @Test
  void consumeMarksAnActiveTicketAsUsed() {
    SignupTicketService service = service();
    SignupTicketModel ticket = activeTicket();
    when(tickets.lockByTicketHash(SecureTokens.hash("raw-ticket"))).thenReturn(Optional.of(ticket));
    when(tickets.save(any(SignupTicketModel.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<SignupTicketModel> consumed = service.consume("raw-ticket");

    assertThat(consumed).isPresent();
    assertThat(consumed.orElseThrow().getConsumedAt()).isNotNull();
  }

  @Test
  void consumeRejectsAnAlreadyConsumedTicket() {
    SignupTicketService service = service();
    SignupTicketModel ticket = activeTicket();
    ticket.setConsumedAt(Instant.now().minus(Duration.ofMinutes(1)));
    when(tickets.lockByTicketHash(SecureTokens.hash("raw-ticket"))).thenReturn(Optional.of(ticket));

    assertThat(service.consume("raw-ticket")).isEmpty();
  }

  @Test
  void consumeRejectsAnExpiredTicket() {
    SignupTicketService service = service();
    SignupTicketModel ticket = activeTicket();
    ticket.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
    when(tickets.lockByTicketHash(SecureTokens.hash("raw-ticket"))).thenReturn(Optional.of(ticket));

    assertThat(service.consume("raw-ticket")).isEmpty();
  }

  @Test
  void consumeRejectsAnUnknownOrBlankTicket() {
    SignupTicketService service = service();
    when(tickets.lockByTicketHash(SecureTokens.hash("raw-ticket"))).thenReturn(Optional.empty());

    assertThat(service.consume("raw-ticket")).isEmpty();
    assertThat(service.consume(null)).isEmpty();
    assertThat(service.consume("  ")).isEmpty();
  }

  private SignupTicketModel activeTicket() {
    SignupTicketModel ticket = new SignupTicketModel();
    ticket.setTicketHash(SecureTokens.hash("raw-ticket"));
    ticket.setProvider(AuthProvider.GOOGLE);
    ticket.setProviderUid("google-subject-1");
    ticket.setEmail("person@gmail.com");
    ticket.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
    return ticket;
  }
}
