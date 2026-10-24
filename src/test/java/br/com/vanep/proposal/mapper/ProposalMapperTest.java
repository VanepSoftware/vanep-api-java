package br.com.vanep.proposal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.proposal.dto.ProposalSummaryResponseDTO;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.proposal.repository.ProposalRepository;
import br.com.vanep.schedule.mapper.ScheduleMapper;
import br.com.vanep.servicerequest.mapper.ServiceRequestMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProposalMapperTest {

  private static final Instant VALID_UNTIL = Instant.parse("2026-11-01T12:00:00Z");
  private static final LocalDate STARTS_ON = LocalDate.of(2027, 2, 1);

  private final ProposalMapper mapper =
      new ProposalMapper(new ScheduleMapper(), new ServiceRequestMapper());

  @ParameterizedTest
  @CsvSource({"4200.00, 10, 420.00", "1000.00, 3, 333.33", "100.00, 1, 100.00", "0.05, 2, 0.02"})
  void theInstallmentAmountIsTheTotalSplitWithTheCentsTruncated(
      String total, int installments, String expected) {
    assertThat(mapper.installmentAmountOf(new BigDecimal(total), installments))
        .isEqualByComparingTo(expected)
        .hasScaleOf(2);
  }

  @Test
  void theSummaryCarriesTheTermsTheListShows() {
    ProposalSummaryResponseDTO summary =
        mapper.toSummaryResponse(row(ProposalStatus.SENT, new BigDecimal("8000.00"), 10, false));

    assertThat(summary.token()).isEqualTo("proposal");
    assertThat(summary.version()).isEqualTo(2);
    assertThat(summary.status()).isEqualTo(ProposalStatus.SENT);
    assertThat(summary.totalAmount()).isEqualByComparingTo("8000.00");
    assertThat(summary.installments()).isEqualTo(10);
    assertThat(summary.installmentAmount()).isEqualByComparingTo("800.00");
    assertThat(summary.validUntil()).isEqualTo(VALID_UNTIL);
    assertThat(summary.startsOn()).isEqualTo(STARTS_ON);
    assertThat(summary.hasPendingAdjustment()).isFalse();
  }

  @Test
  void anAdjustmentIsPendingOnlyWhileTheProposalIsSent() {
    assertThat(
            mapper
                .toSummaryResponse(row(ProposalStatus.SENT, BigDecimal.TEN, 1, true))
                .hasPendingAdjustment())
        .isTrue();
    assertThat(
            mapper
                .toSummaryResponse(row(ProposalStatus.ACCEPTED, BigDecimal.TEN, 1, true))
                .hasPendingAdjustment())
        .isFalse();
  }

  private ProposalRepository.CurrentProposalRow row(
      ProposalStatus status, BigDecimal totalAmount, int installments, boolean hasAdjustments) {
    return new ProposalRepository.CurrentProposalRow() {
      @Override
      public Long getServiceRequestId() {
        return 50L;
      }

      @Override
      public String getToken() {
        return "proposal";
      }

      @Override
      public Integer getVersion() {
        return 2;
      }

      @Override
      public ProposalStatus getStatus() {
        return status;
      }

      @Override
      public BigDecimal getTotalAmount() {
        return totalAmount;
      }

      @Override
      public Integer getInstallments() {
        return installments;
      }

      @Override
      public Instant getValidUntil() {
        return VALID_UNTIL;
      }

      @Override
      public LocalDate getStartsOn() {
        return STARTS_ON;
      }

      @Override
      public boolean getHasAdjustments() {
        return hasAdjustments;
      }
    };
  }
}
