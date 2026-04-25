package ru.finam.trade.hedging.last_price;

import grpc.tradeapi.v1.marketdata.SubscribeQuoteResponse;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.DateUtils;
import ru.finam.trade.common.ErrorUtils;
import ru.finam.trade.core.stream.StreamProcessor;
import ru.finam.trade.hedging.HedgingStats;
import ru.finam.trade.hedging.HedgingStrategyStorage;
import ru.finam.trade.notification.NotificationService;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "hedging.enabled", havingValue = "true")
@Slf4j
public class LastPriceListener {
    private final FinamApi api;
    private final NotificationService notificationService;
    private final List<String> symbolList;
    private final StreamProcessor<SubscribeQuoteResponse> processor;
    private final ScheduledExecutorService executorService;
    private final List<LastPriceObserver> lastPriceObservers;

    @SneakyThrows
    public LastPriceListener(NotificationService notificationService,
                             FinamApi api,
                             Map<String, HedgingStats> hedgingStatsMap,
                             HedgingStrategyStorage hedgingStrategyStorage,
                             LastPriceStorage lastPriceStorage
    ) {
        this.api = api;
        this.notificationService = notificationService;
        executorService = Executors.newScheduledThreadPool(1);
        lastPriceObservers = new ArrayList<>(hedgingStatsMap.values());
        lastPriceObservers.add(lastPriceStorage);
        symbolList = hedgingStrategyStorage.getSymbolList();

        processor = response -> Optional.ofNullable(response)
                .ifPresent(r -> {
                    val now = DateUtils.nowDate();
                    r.getQuoteList().stream()
                            .map(q -> Tuple2.of(DateUtils.timestampToZonedDateTime(q.getTimestamp()), q))
                            .filter(t -> t.getItem1().toLocalDate().equals(now))
                            .sorted(Comparator.comparing(Tuple2::getItem1))
                            .map(Tuple2::getItem2)
                            .forEach(quote -> {
                                        lastPriceObservers.forEach(lastPriceObserver -> lastPriceObserver.onChange(quote));
                                        hedgingStrategyStorage.buyOrSell(quote.getSymbol());
                                    }
                            );
                });
    }

    @PostConstruct
    public void startToListen() {
        if (api.getMarketDataStreamService().existSubscription()) {
            return;
        }

        api.getMarketDataStreamService().subscribeQuote(processor, this::onError, symbolList);
    }

    public void stopToListen() {
        api.getMarketDataStreamService().cancelSubscription();
    }

    public void onError(Throwable error) {
        if (ErrorUtils.isNeedLoggingError(error)) {
            log.error("last price error", error);
            notificationService.sendMessage(error.toString());
        }
        stopToListen();

        executorService.schedule(this::startToListen, 5, TimeUnit.SECONDS);
    }
}
