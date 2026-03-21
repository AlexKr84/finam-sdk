package ru.finam.trade.hedging.trade;

import grpc.tradeapi.v1.orders.SubscribeTradesResponse;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.ErrorUtils;
import ru.finam.trade.core.stream.StreamProcessor;
import ru.finam.trade.notification.NotificationService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "hedging.enabled", havingValue = "true")
@Slf4j
public class TradeListener {
    private final FinamApi api;
    private final NotificationService notificationService;
    private final StreamProcessor<SubscribeTradesResponse> processor;
    private final ScheduledExecutorService executorService;

    @SneakyThrows
    public TradeListener(NotificationService notificationService,
                         FinamApi api,
                         TradeObserver tradeObserver
    ) {
        this.api = api;
        this.notificationService = notificationService;
        executorService = Executors.newScheduledThreadPool(1);

        processor = response -> response.getTradesList().forEach(tradeObserver::onTrade);
    }

    @PostConstruct
    public void startToListen() {
        if (api.getOrdersStreamService().existTradeSubscription()) {
            return;
        }

        api.getOrdersStreamService().subscribeTrade(processor, this::onError, api.getAccountId());
    }

    public void stopToListen() {
        api.getOrdersStreamService().cancelTradeSubscription();
    }

    public void onError(Throwable error) {
        if (ErrorUtils.isNeedLoggingError(error)) {
            log.error("trade error", error);
            notificationService.sendMessage(error.toString());
        }
        stopToListen();

        executorService.schedule(this::startToListen, 5, TimeUnit.SECONDS);
    }
}
