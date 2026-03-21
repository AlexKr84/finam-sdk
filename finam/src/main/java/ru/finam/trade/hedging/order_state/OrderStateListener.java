package ru.finam.trade.hedging.order_state;

import grpc.tradeapi.v1.orders.SubscribeOrdersResponse;
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
public class OrderStateListener {
    private final FinamApi api;
    private final NotificationService notificationService;
    private final StreamProcessor<SubscribeOrdersResponse> processor;
    private final ScheduledExecutorService executorService;

    @SneakyThrows
    public OrderStateListener(NotificationService notificationService,
                              FinamApi api,
                              OrderStateObserver orderStateObserver
    ) {
        this.api = api;
        this.notificationService = notificationService;
        executorService = Executors.newScheduledThreadPool(1);

        processor = response -> response.getOrdersList().forEach(orderStateObserver::onChangeOrderState);
    }

    @PostConstruct
    public void startToListen() {
        if (api.getOrdersStreamService().existOrderSubscription()) {
            return;
        }

        api.getOrdersStreamService().subscribeOrder(processor, this::onError, api.getAccountId());
    }

    public void stopToListen() {
        api.getOrdersStreamService().cancelOrderSubscription();
    }

    public void onError(Throwable error) {
        if (ErrorUtils.isNeedLoggingError(error)) {
            log.error("order state error", error);
            notificationService.sendMessage(error.toString());
        }
        stopToListen();

        executorService.schedule(this::startToListen, 5, TimeUnit.SECONDS);
    }
}
