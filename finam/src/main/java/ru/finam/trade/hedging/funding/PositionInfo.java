package ru.finam.trade.hedging.funding;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record PositionInfo(
  int volume,
  BigDecimal price,
  ZonedDateTime date
) {
}
