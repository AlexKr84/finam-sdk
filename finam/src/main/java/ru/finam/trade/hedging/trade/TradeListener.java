package ru.finam.trade.hedging.trade;

import grpc.tradeapi.v1.orders.SubscribeTradesResponse;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.AbstractListener;
import ru.finam.trade.core.stream.StreamProcessor;
import ru.finam.trade.notification.NotificationService;

@Service
@ConditionalOnProperty(name = "hedging.enabled", havingValue = "true")
@Slf4j
public class TradeListener extends AbstractListener {
    private final FinamApi api;
    private final StreamProcessor<SubscribeTradesResponse> processor;

    @SneakyThrows
    public TradeListener(NotificationService notificationService,
                         FinamApi api,
                         TradeObserver tradeObserver
    ) {
        super(notificationService);
        this.api = api;

        processor = response -> response.getTradesList().forEach(tradeObserver::onTrade);
    }

    @PostConstruct
    @Override
    public void startToListen() {
        if (api.getOrdersStreamService().existTradeSubscription()) {
            return;
        }

        api.getOrdersStreamService().subscribeTrade(processor, this::onError, api.getAccountId());
    }

    @Override
    public void stopToListen() {
        api.getOrdersStreamService().cancelTradeSubscription();
    }

    @Override
    public String getPrefixError() {
        return "trade error";
    }
}
