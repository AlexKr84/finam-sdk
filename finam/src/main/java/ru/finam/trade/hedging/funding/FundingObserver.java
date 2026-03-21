package ru.finam.trade.hedging.funding;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface FundingObserver {
  void onUpdateFunding(LocalDate date, BigDecimal funding);
  PositionInfo getCurrentPosition();
}
