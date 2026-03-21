package ru.finam.trade.hedging.diff;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

@Getter
public class AdvancedHedgingDiff extends HedgingDiff {
    @Setter
    private int currentVolume;
    @Setter
    private int futureVolume;
    private final ZonedDateTime date;
    private BigDecimal funding;
    private final boolean isInverse;

    @Builder
    public AdvancedHedgingDiff(BigDecimal currentPrice,
                               BigDecimal futurePrice,
                               BigDecimal multiplier,
                               int currentVolume,
                               int futureVolume,
                               ZonedDateTime date,
                               BigDecimal funding,
                               boolean isInverse) {
        super(currentPrice, futurePrice, multiplier, false);
        this.currentVolume = currentVolume;
        this.futureVolume = futureVolume;
        this.date = date;
        this.funding = funding;
        this.isInverse = isInverse;
        updateValue();
    }

    public void setFunding(BigDecimal funding) {
        this.funding = funding;
        updateValue();
    }

    @Override
    protected BigDecimal calcValue() {
        val res = super.calcValue();
        return Optional.ofNullable(funding)
                .map(res::subtract)
                .orElse(res);
    }
}
