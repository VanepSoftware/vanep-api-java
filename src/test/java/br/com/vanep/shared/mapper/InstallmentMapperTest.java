package br.com.vanep.shared.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstallmentMapperTest {

  private final InstallmentMapper mapper = new InstallmentMapper();

  @ParameterizedTest
  @CsvSource({
    "4200.00, 10, 420.00, 420.00",
    "1000.00, 3, 333.33, 333.34",
    "4400.00, 12, 366.66, 366.74",
    "100.00, 1, 100.00, 100.00",
    "0.05, 2, 0.02, 0.03"
  })
  void theCentsAreTruncatedAndTheLastInstallmentTakesTheRemainder(
      String total, int installments, String amount, String lastAmount) {
    BigDecimal totalAmount = new BigDecimal(total);

    assertThat(mapper.amountOf(totalAmount, installments))
        .isEqualByComparingTo(amount)
        .hasScaleOf(2);
    assertThat(mapper.lastAmountOf(totalAmount, installments))
        .isEqualByComparingTo(lastAmount)
        .hasScaleOf(2);
    assertThat(
            mapper
                .amountOf(totalAmount, installments)
                .multiply(BigDecimal.valueOf(installments - 1L))
                .add(mapper.lastAmountOf(totalAmount, installments)))
        .isEqualByComparingTo(total);
  }
}
