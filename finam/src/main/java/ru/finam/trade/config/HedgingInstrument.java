package ru.finam.trade.config;


import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.Optional;

public record HedgingInstrument(
        String currentTicker,
        String futureTicker,
        String anotherFutureTicker,
        BigDecimal multiplier,
        int instanceCount,
        BigDecimal addToDiff,
        HedgingStrategyConfiguration strategy
) {
    public HedgingInstrument(String currentTicker, String futureTicker, String anotherFutureTicker, BigDecimal multiplier, int instanceCount, BigDecimal addToDiff, HedgingStrategyConfiguration strategy) {
        this.currentTicker = currentTicker;
        this.futureTicker = futureTicker;
        this.anotherFutureTicker = anotherFutureTicker;
        this.multiplier = multiplier;
        this.instanceCount = instanceCount;
        this.addToDiff = Optional.ofNullable(addToDiff).orElse(BigDecimal.ZERO);
        this.strategy = strategy;
    }

    @JsonIgnore
    public String getKey() {
        return currentTicker.concat(futureTicker);
    }

    public static HedgingInstrument of(HedgingInstrument hedgingInstrument, int instanceCount, HedgingStrategyConfiguration hedgingStrategyConfiguration) {
        return new HedgingInstrument(
                hedgingInstrument.currentTicker,
                hedgingInstrument.futureTicker,
                hedgingInstrument.anotherFutureTicker,
                hedgingInstrument.multiplier,
                instanceCount,
                hedgingInstrument.addToDiff,
                hedgingStrategyConfiguration
        );
    }
}
