package ru.finam.trade.hedging.diff;

import com.google.protobuf.Timestamp;
import grpc.tradeapi.v1.marketdata.Bar;
import io.smallrye.mutiny.tuples.Tuple2;
import lombok.Getter;
import lombok.val;
import ru.finam.trade.common.DecimalUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.Map;
import java.util.stream.Stream;

@Getter
public class DiffStats {
    private BigDecimal minDiff = BigDecimal.valueOf(Long.MAX_VALUE);
    private BigDecimal maxDiff = BigDecimal.valueOf(Long.MIN_VALUE);
    private BigDecimal maxDiffToMin = BigDecimal.valueOf(Long.MIN_VALUE);
    private BigDecimal maxDiffToMax = BigDecimal.valueOf(Long.MIN_VALUE);
    private final HedgingDiff lastDiff;

    public DiffStats(BigDecimal multiplier) {
        lastDiff = new HedgingDiff(multiplier);
    }

    public void updateStats(Map<Timestamp, Bar> currentCandleMap, Map<Timestamp, Bar> futureCandleMap) {
        val minCandleMap = Stream.of(currentCandleMap, futureCandleMap)
                .min(Comparator.comparingInt(Map::size))
                .orElse(Map.of());

        val diffs = minCandleMap.keySet().stream()
                .map(time -> Tuple2.of(
                        futureCandleMap.get(time),
                        currentCandleMap.get(time)))
                .filter(tuple -> tuple.getItem1() != null && tuple.getItem2() != null)
                .map(tuple -> calcDiff(tuple.getItem1(), tuple.getItem2()))
                .mapToDouble(BigDecimal::doubleValue)
                .toArray();
        val stats = Arrays.stream(diffs)
                .collect(ExtDoubleSummaryStatistics::new, ExtDoubleSummaryStatistics::accept,
                        ExtDoubleSummaryStatistics::combine);
        minDiff = toBigDecimal(stats.getMin());
        maxDiffToMin = toBigDecimal(stats.getMaxDiffToMin());
        maxDiff = toBigDecimal(stats.getMax());
        maxDiffToMax = toBigDecimal(stats.getMaxDiffToMax());
    }

    private static BigDecimal toBigDecimal(double value) {
        if (Double.POSITIVE_INFINITY == value) {
            return BigDecimal.valueOf(Long.MAX_VALUE);
        }
        if (Double.NEGATIVE_INFINITY == value) {
            return BigDecimal.valueOf(Long.MIN_VALUE);
        }
        return BigDecimal.valueOf(value);
    }

    private BigDecimal calcDiff(Bar futureCandle, Bar currentCandle) {
        val futurePrice = DecimalUtils.toBigDecimal(futureCandle.getClose());
        val currentPrice = DecimalUtils.toBigDecimal(currentCandle.getClose());
        val diff = new HedgingDiff(currentPrice, futurePrice, lastDiff.getMultiplier());
        return diff.getValue();
    }

    public boolean updateLastDiff(BigDecimal currentPrice, BigDecimal futurePrice) {
        var lastDiffChange = false;
        if (currentPrice != null) {
            lastDiff.setCurrentPrice(currentPrice);
            lastDiffChange = true;
        }
        if (futurePrice != null) {
            lastDiff.setFuturePrice(futurePrice);
            lastDiffChange = true;
        }
        val diff = lastDiff.getValue();
        if (!lastDiffChange || diff == null) {
            return false;
        }
        var statsChange = false;
        if (diff.compareTo(minDiff) < 0) {
            minDiff = diff;
            maxDiffToMin = BigDecimal.ZERO;
            statsChange = true;
        } else if (diff.compareTo(maxDiffToMin.add(minDiff)) > 0) {
            maxDiffToMin = diff.subtract(minDiff);
            statsChange = true;
        }
        if (diff.compareTo(maxDiff) > 0) {
            maxDiff = diff;
            maxDiffToMax = BigDecimal.ZERO;
            statsChange = true;
        } else if (diff.compareTo(maxDiff.subtract(maxDiffToMax)) < 0) {
            maxDiffToMax = maxDiff.subtract(diff);
            statsChange = true;
        }
        return statsChange;
    }


    @Getter
    public static class ExtDoubleSummaryStatistics extends DoubleSummaryStatistics {
        private Double maxDiffToMin = Double.NEGATIVE_INFINITY;
        private Double maxDiffToMax = Double.NEGATIVE_INFINITY;

        @Override
        public void accept(double value) {
            if (value < getMin()) {
                maxDiffToMin = 0d;
            } else if (value > getMin() + maxDiffToMin) {
                maxDiffToMin = value - getMin();
            }
            if (value > getMax()) {
                maxDiffToMax = 0d;
            } else if (value < getMax() - maxDiffToMax) {
                maxDiffToMax = getMax() - value;
            }
            super.accept(value);
        }

        @Override
        public void combine(DoubleSummaryStatistics other) {
            throw new UnsupportedOperationException();
        }
    }
}
