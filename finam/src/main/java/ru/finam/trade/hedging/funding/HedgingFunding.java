package ru.finam.trade.hedging.funding;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.core.UriBuilder;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.DateUtils;
import ru.finam.trade.common.DecimalUtils;
import ru.finam.trade.instrument.InstrumentInfo;
import ru.finam.trade.notification.NotificationService;

import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class HedgingFunding {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());
    // private static final Proxy PROXY = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("85.174.84.22", 1080));

    private final FinamApi api;
    private final NotificationService notificationService;
    private final InstrumentInfo currentInstrument;
    private final List<FundingObserver> fundingObservers;

    public HedgingFunding(FinamApi api,
                          NotificationService notificationService,
                          InstrumentInfo currentInstrument) {
        this.api = api;
        this.notificationService = notificationService;
        this.currentInstrument = currentInstrument;
        fundingObservers = new ArrayList<>();
    }

    public String getCurrentSymbol() {
        return currentInstrument.getSymbol();
    }

    public String getCurrentTicker() {
        return currentInstrument.getTicker();
    }

    public void addFundingObservers(Collection<? extends FundingObserver> fundingObservers) {
        this.fundingObservers.addAll(fundingObservers);
    }

    public void updateFunding(boolean withFuturePayment) {
        val account = api.getAccountsService().getAccount(api.getAccountId());
        account.getPositionsList().stream()
                .filter(p -> p.getSymbol().equals(getCurrentSymbol()))
                .findFirst()
                .flatMap(position -> {
                    val nowLocalDate = DateUtils.now().toLocalDate();
                    val lastQuote = api.getMarketDataService().getLastQuote(getCurrentSymbol());
                    val lastDate = DateUtils.timestampToZonedDateTime(lastQuote.getTimestamp()).toLocalDate();
                    if (withFuturePayment && !lastDate.equals(nowLocalDate)) {
                        return Optional.empty();
                    }
                    val positionMap = fundingObservers.stream()
                            .map(FundingObserver::getCurrentPosition)
                            .filter(Objects::nonNull)
                            .map(PositionAgg::new)
                            .collect(Collectors.groupingBy(
                                    p -> p.getDate().toLocalDate(),
                                    TreeMap::new,
                                    Collectors.reducing(PositionAgg.emptyPositionAgg, PositionAgg::add)));
                    val fullPositionAgg = positionMap.values().stream()
                            .reduce(PositionAgg.emptyPositionAgg, PositionAgg::add);
                    val quantity = DecimalUtils.toBigDecimal(position.getQuantity());
                    if (Objects.requireNonNull(quantity).compareTo(fullPositionAgg.getQuantity()) != 0) {
                        val message = String.format("quantity not equals %.2f and %.2f", quantity, fullPositionAgg.getQuantity());
                        notificationService.sendMessage(message);
                        log.error(message);
                        return Optional.empty();
                    }
                    if (fullPositionAgg.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                        val message = "quantity equals 0";
                        notificationService.sendMessage(message);
                        log.error(message);
                        return Optional.empty();
                    }
                    val fundingMap = getFundings(getCurrentTicker(), fullPositionAgg.date);
                    if (withFuturePayment && !fundingMap.containsKey(nowLocalDate)) {
                        val newFunding = getValueByScheduledService(() ->
                                Optional.ofNullable(getFundings(getCurrentTicker(), DateUtils.localDateToZonedDateTime(nowLocalDate)).get(nowLocalDate)));
                        fundingMap.put(nowLocalDate, newFunding);
                    }
                    Map<LocalDate, BigDecimal> res = new HashMap<>();
                    fundingMap.forEach((date, funding) -> {
                        res.forEach((key, value) -> res.put(key, value.add(funding)));
                        if (positionMap.containsKey(date)) {
                            res.put(date, funding);
                        }
                    });
                    return Optional.of(res);
                })
                .ifPresent(fundings ->
                        fundings.forEach((date, funding) -> {
                            fundingObservers.forEach(o -> o.onUpdateFunding(date, funding));
                            notificationService.sendMessage(String.format("%s date = %s funding = %.2f",
                                    getCurrentSymbol(), DateUtils.localDateToString(date), funding));
                        }));
    }

    @SneakyThrows
    private <V> V getValueByScheduledService(Callable<Optional<V>> callable) {
        val executorService = Executors.newScheduledThreadPool(1);
        do {
            try {
                val result = executorService.schedule(callable, 1, TimeUnit.MINUTES).get();
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("get value by scheduled service", e);
            }
        } while (true);
    }

    @SneakyThrows
    private Map<LocalDate, BigDecimal> getFundings(String ticker, ZonedDateTime from) {
        // https://iss.moex.com/iss/history/engines/futures/markets/forts/boards/RFUD/securities/USDRUBF.json?from=2026-02-05&iss.meta=off&history.columns=TRADEDATE,SWAPRATE
        UriBuilder builder = UriBuilder
                .fromUri("https://iss.moex.com/iss/history/engines/futures/markets/forts/boards/RFUD/securities")
                .path("/{ticker}.json")
                .queryParam("from", DateUtils.localDateToString(from.toLocalDate()))
                .queryParam("iss.meta", "off")
                .queryParam("history.columns", "TRADEDATE,SWAPRATE");

        val url = builder.build(ticker).toURL();
        val connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            for (var i = 0; ; i++) {
                try (val inputStream = connection.getInputStream()) {
                    val bytes = inputStream.readAllBytes();
                    val response = new String(bytes);
                    val historyInfo = OBJECT_MAPPER.readValue(response, HistoryFutureResponse.class).history;
                    return historyInfo.data.stream()
                            .collect(Collectors.toMap(
                                    HistoryFutureResponse.FundingInfo::getDate,
                                    f -> f.funding.multiply(currentInstrument.getBasicAssetSize()),
                                    (v1, v2) -> {
                                        throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));
                                    },
                                    TreeMap::new));
                } catch (Exception ex) {
                    if (i == 2) {
                        throw new RuntimeException(ex);
                    }
                    log.error("get funding error", ex);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class HistoryFutureResponse {
        private HistoryFutureInfo history;

        @NoArgsConstructor
        @AllArgsConstructor
        @Data
        public static class HistoryFutureInfo {
            private List<FundingInfo> data;
        }

        @NoArgsConstructor
        @AllArgsConstructor
        @Data
        @JsonFormat(shape = JsonFormat.Shape.ARRAY)
        public static class FundingInfo {
            private LocalDate date;
            private BigDecimal funding;
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class PositionAgg {
        private static final PositionAgg emptyPositionAgg = new PositionAgg(BigDecimal.ZERO, BigDecimal.ZERO, null);

        private BigDecimal quantity;
        private BigDecimal amount;
        private ZonedDateTime date;

        public PositionAgg(PositionInfo positionInfo) {
            quantity = BigDecimal.valueOf(positionInfo.volume());
            amount = positionInfo.price().multiply(BigDecimal.valueOf(positionInfo.volume()));
            date = positionInfo.date();
        }

        public static PositionAgg add(PositionAgg positionAgg1, PositionAgg positionAgg2) {
            val quantity = positionAgg1.quantity.add(positionAgg2.quantity);
            val amount = positionAgg1.amount.add(positionAgg2.amount);
            val date = least(positionAgg1.date, positionAgg2.date);
            return new PositionAgg(quantity, amount, date);
        }

        public static ZonedDateTime least(ZonedDateTime a, ZonedDateTime b) {
            return a == null ? b : (b == null ? a : (a.isBefore(b) ? a : b));
        }
    }
}
