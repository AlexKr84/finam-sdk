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
import ru.finam.trade.common.AbstractListener;
import ru.finam.trade.common.DateUtils;
import ru.finam.trade.core.stream.StreamProcessor;
import ru.finam.trade.hedging.HedgingStats;
import ru.finam.trade.hedging.HedgingStrategyStorage;
import ru.finam.trade.notification.NotificationService;

import java.util.*;

@Service
@ConditionalOnProperty(name = "hedging.enabled", havingValue = "true")
@Slf4j
public class LastPriceListener extends AbstractListener {
    private final FinamApi api;
    private final List<String> symbolList;
    private final StreamProcessor<SubscribeQuoteResponse> processor;
    private final List<LastPriceObserver> lastPriceObservers;

    @SneakyThrows
    public LastPriceListener(NotificationService notificationService,
                             FinamApi api,
                             Map<String, HedgingStats> hedgingStatsMap,
                             HedgingStrategyStorage hedgingStrategyStorage,
                             LastPriceStorage lastPriceStorage
    ) {
        super(notificationService);
        this.api = api;
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
    @Override
    public void startToListen() {
        if (api.getMarketDataStreamService().existSubscription()) {
            return;
        }

        api.getMarketDataStreamService().subscribeQuote(processor, this::onError, symbolList);
    }

    @Override
    public void stopToListen() {
        api.getMarketDataStreamService().cancelSubscription();
    }

    @Override
    public String getPrefixError() {
        return "last price error";
    }
}
