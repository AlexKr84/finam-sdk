package ru.finam.trade.hedging;

import com.google.protobuf.Timestamp;
import grpc.tradeapi.v1.marketdata.Bar;
import grpc.tradeapi.v1.marketdata.Quote;
import grpc.tradeapi.v1.marketdata.TimeFrame;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.DateUtils;
import ru.finam.trade.common.DecimalUtils;
import ru.finam.trade.config.HedgingInstrument;
import ru.finam.trade.hedging.diff.DiffStats;
import ru.finam.trade.hedging.last_price.LastPriceObserver;
import ru.finam.trade.instrument.InstrumentInfo;
import ru.finam.trade.instrument.InstrumentStorage;
import ru.finam.trade.notification.NotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class HedgingStats implements LastPriceObserver {
    private static final LocalTime START_TIME_MARKET = LocalTime.parse("09:00:20");

    private final FinamApi api;
    private final InstrumentStorage instrumentStorage;
    private final NotificationService notificationService;
    @Getter
    private final InstrumentInfo currentInstrument;
    @Getter
    private final InstrumentInfo futureInstrument;
    @Getter
    private final InstrumentInfo anotherFutureInstrument;
    @Getter
    private final BigDecimal multiplier;
    @Getter
    private DiffStats diffStats;
    @Getter
    private DiffStats previousDiffStats;
    @Getter
    private ContangoStats contangoStats;
    private ZonedDateTime changeDiffStatsTime;

    public HedgingStats(FinamApi api,
                        NotificationService notificationService,
                        InstrumentStorage instrumentStorage,
                        HedgingInstrument hedgingInstrument) {
        this.api = api;
        this.instrumentStorage = instrumentStorage;
        this.notificationService = notificationService;
        currentInstrument = geInstrumentByTicker(hedgingInstrument.currentTicker());
        futureInstrument = geInstrumentByTicker(hedgingInstrument.futureTicker());
        anotherFutureInstrument = geInstrumentByTicker(hedgingInstrument.anotherFutureTicker());
        multiplier = hedgingInstrument.multiplier();
        updateStats();
    }

    public String getCurrentSymbol() {
        return currentInstrument.getSymbol();
    }

    public String getFutureSymbol() {
        return futureInstrument.getSymbol();
    }

    public String getAnotherFutureSymbol() {
        return anotherFutureInstrument.getSymbol();
    }

    private InstrumentInfo geInstrumentByTicker(String ticker) {
        return instrumentStorage.getInstrument("RTSX", ticker);
    }

    public void updateStats() {
        var date = DateUtils.now();
        diffStats = Objects.requireNonNullElseGet(updateStats(date), () -> new DiffStats(multiplier));
        do {
            date = date.minusDays(1);
            previousDiffStats = updateStats(date);
        } while (previousDiffStats == null);
        updateContango();
    }

    private DiffStats updateStats(ZonedDateTime date) {
        val dateTruncated = date.truncatedTo(ChronoUnit.DAYS).plusDays(1);
        val to = dateTruncated.minusSeconds(1);
        val from = dateTruncated.minusDays(1);
        val currentCandleMap = getCandleMap(getCurrentSymbol(), from, to);
        val futureCandleMap = getCandleMap(getFutureSymbol(), from, to);
        if (currentCandleMap.isEmpty() || futureCandleMap.isEmpty()) {
            return null;
        }
        val diffStats = new DiffStats(multiplier);
        diffStats.updateStats(currentCandleMap, futureCandleMap);
        return diffStats;
    }

    private Map<Timestamp, Bar> getCandleMap(String symbol, ZonedDateTime from, ZonedDateTime to) {
        return api.getMarketDataService().getBars(symbol, TimeFrame.TIME_FRAME_M1, DateUtils.zonedDateTimeToTimestamp(from), DateUtils.zonedDateTimeToTimestamp(to))
                .stream()
                .collect(Collectors.toMap(
                        Bar::getTimestamp,
                        Function.identity()
                ));
    }

    private void updateContango() {
        val futureLastPrice = getLastPrice(getFutureSymbol());
        val anotherFutureLastPrice = getLastPrice(getAnotherFutureSymbol());
        val betweenFutureDays = getBetweenDays(futureInstrument.getLastTradeDate(), anotherFutureInstrument.getLastTradeDate());
        val contangoDay = anotherFutureLastPrice.subtract(futureLastPrice).abs().divide(betweenFutureDays, 4, RoundingMode.HALF_UP);
        contangoStats = new ContangoStats(contangoDay, betweenFutureDays.multiply(contangoDay));
    }

    public BigDecimal getRemainingDays() {
        return getBetweenDays(DateUtils.now(), futureInstrument.getLastTradeDate());
    }

    private BigDecimal getLastPrice(String symbol) {
        return DecimalUtils.toBigDecimal(api.getMarketDataService().getLastQuote(symbol).getLast());
    }

    private static BigDecimal getBetweenDays(ZonedDateTime date1, ZonedDateTime date2) {
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(date1, date2)).abs();
    }

    @Override
    public void onChange(Quote quote) {
        if (!isStartTradeMarket()) {
            return;
        }
        updateLastDiff(quote);
    }

    private static boolean isStartTradeMarket() {
        return DateUtils.now().toLocalTime().isAfter(START_TIME_MARKET);
    }

    private void updateLastDiff(Quote quote) {
        val last = DecimalUtils.toBigDecimal(quote.getLast());
        if (last == null) {
            return;
        }
        val currentPrice = quote.getSymbol().equals(getCurrentSymbol()) ? last : null;
        val futurePrice = quote.getSymbol().equals(getFutureSymbol()) ? last : null;
        if (diffStats.updateLastDiff(currentPrice, futurePrice)) {
            changeDiffStatsTime = DateUtils.now();
        }
    }

    public void notifyMaxMinDiff() {
        val now = DateUtils.now();
        if (changeDiffStatsTime != null && changeDiffStatsTime.isBefore(now.minusSeconds(5))) {
            notificationService.sendMessage(String.format("%s maxDiff = %.2f, maxDiffToMax = %.2f, minDiff = %.2f, maxDiffToMin = %.2f ",
                    getCurrentSymbol(), diffStats.getMaxDiff(), diffStats.getMaxDiffToMax(), diffStats.getMinDiff(), diffStats.getMaxDiffToMin()));
            changeDiffStatsTime = null;
        }
    }

    public record ContangoStats(
            BigDecimal contangoDay,
            BigDecimal contango
    ) {
    }
}
