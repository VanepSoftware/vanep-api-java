package br.com.vanep.shared.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

// Cents are truncated and the last installment takes the remainder, so the installments always
// add up to the total.
@Component
public class InstallmentMapper {

  public BigDecimal amountOf(BigDecimal totalAmount, Integer installments) {
    return totalAmount.divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN);
  }

  public BigDecimal lastAmountOf(BigDecimal totalAmount, Integer installments) {
    BigDecimal others =
        amountOf(totalAmount, installments).multiply(BigDecimal.valueOf(installments - 1L));
    return totalAmount.subtract(others).setScale(2, RoundingMode.UNNECESSARY);
  }
}
