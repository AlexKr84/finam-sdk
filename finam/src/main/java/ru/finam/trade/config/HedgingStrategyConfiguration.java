package ru.finam.trade.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import ru.finam.trade.hedging.diff.AdvancedHedgingDiff;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;

public record HedgingStrategyConfiguration(
        int currentPositionSize,
        int futurePositionSize,
        boolean withExitPosition,
        boolean sellFuture,
        BigDecimal addToRemainingDays,
        BigDecimal diffToMax,
        BigDecimal diffToMin,
        BigDecimal needDiff,
        BigDecimal diffToMaxInverse,
        BigDecimal diffToMinInverse,
        BigDecimal needDiffInverse,
        OrderDiff orderDiff
) {
    public HedgingStrategyConfiguration(int currentPositionSize,
                                        int futurePositionSize,
                                        boolean withExitPosition,
                                        boolean sellFuture,
                                        BigDecimal addToRemainingDays,
                                        BigDecimal diffToMax,
                                        BigDecimal diffToMin,
                                        BigDecimal needDiff,
                                        BigDecimal diffToMaxInverse,
                                        BigDecimal diffToMinInverse,
                                        BigDecimal needDiffInverse,
                                        OrderDiff orderDiff
    ) {
        this.currentPositionSize = currentPositionSize;
        this.futurePositionSize = futurePositionSize;
        this.withExitPosition = withExitPosition;
        this.sellFuture = sellFuture;
        this.addToRemainingDays = addToRemainingDays;
        this.diffToMax = Objects.requireNonNullElse(diffToMax, BigDecimal.ZERO);
        this.diffToMin = Objects.requireNonNullElse(diffToMin, BigDecimal.ZERO);
        this.needDiff = needDiff;
        this.diffToMaxInverse = Objects.requireNonNullElse(diffToMaxInverse, BigDecimal.ZERO);
        this.diffToMinInverse = Objects.requireNonNullElse(diffToMinInverse, BigDecimal.ZERO);
        this.needDiffInverse = needDiffInverse;
        this.orderDiff = orderDiff;
    }

    public static HedgingStrategyConfiguration of(HedgingStrategyConfiguration strategyConfiguration, BigDecimal diffToMax) {
        return new HedgingStrategyConfiguration(
                strategyConfiguration.currentPositionSize,
                strategyConfiguration.futurePositionSize,
                strategyConfiguration.withExitPosition,
                strategyConfiguration.sellFuture,
                strategyConfiguration.addToRemainingDays,
                diffToMax,
                strategyConfiguration.diffToMin,
                strategyConfiguration.needDiff,
                strategyConfiguration.diffToMaxInverse,
                strategyConfiguration.diffToMinInverse,
                strategyConfiguration.needDiffInverse,
                strategyConfiguration.orderDiff
        );
    }

    public static HedgingStrategyConfiguration of(HedgingStrategyConfiguration strategyConfiguration, AdvancedHedgingDiff orderDiff) {
        return new HedgingStrategyConfiguration(
                strategyConfiguration.currentPositionSize,
                strategyConfiguration.futurePositionSize,
                strategyConfiguration.withExitPosition,
                strategyConfiguration.sellFuture,
                strategyConfiguration.addToRemainingDays,
                strategyConfiguration.diffToMax,
                strategyConfiguration.diffToMin,
                strategyConfiguration.needDiff,
                strategyConfiguration.diffToMaxInverse,
                strategyConfiguration.diffToMinInverse,
                strategyConfiguration.needDiffInverse,
                new OrderDiff(
                        orderDiff.getCurrentPrice(),
                        orderDiff.getFuturePrice(),
                        orderDiff.getCurrentVolume(),
                        orderDiff.getFutureVolume(),
                        orderDiff.getDate(),
                        orderDiff.isInverse())
        );
    }

    public record OrderDiff(
            BigDecimal currentPrice,
            BigDecimal futurePrice,
            int currentVolume,
            int futureVolume,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX'['VV']'")
            ZonedDateTime date,
            boolean isInverse
    ) {
    }
}
