package ru.finam.trade.hedging.diff;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Getter
public class HedgingDiff {
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private BigDecimal currentPrice;
    private BigDecimal futurePrice;
    private BigDecimal value;
    private final BigDecimal multiplier;

    public HedgingDiff(BigDecimal currentPrice, BigDecimal futurePrice, BigDecimal multiplier) {
        this(currentPrice, futurePrice, multiplier, true);
    }

    public HedgingDiff(BigDecimal currentPrice, BigDecimal futurePrice, BigDecimal multiplier, boolean withUpdateValue) {
        this.currentPrice = currentPrice;
        this.futurePrice = futurePrice;
        this.multiplier = multiplier;
        if (withUpdateValue) {
            updateValue();
        }
    }

    public BigDecimal getNormalizedCurrentPrice() {
        return currentPrice.multiply(multiplier);
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        if (currentPrice == null) {
            return;
        }
        this.currentPrice = currentPrice;
        updateValue();
    }

    public void setFuturePrice(BigDecimal futurePrice) {
        if (futurePrice == null) {
            return;
        }
        this.futurePrice = futurePrice;
        updateValue();
    }

    protected BigDecimal calcValue() {
        if (currentPrice == null || futurePrice == null) {
            return null;
        }
        val max = futurePrice.max(currentPrice);
        val min = futurePrice.min(currentPrice);
        val diff = max.subtract(min.multiply(TWO));
        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            return futurePrice.subtract(currentPrice);
        }
        return futurePrice.subtract(currentPrice.multiply(multiplier));
    }

    protected void updateValue() {
        value = calcValue();
    }
}
