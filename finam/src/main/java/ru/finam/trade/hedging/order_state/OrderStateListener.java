package ru.finam.trade.hedging.order_state;

import grpc.tradeapi.v1.orders.OrderStatus;
import grpc.tradeapi.v1.orders.SubscribeOrdersResponse;
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
public class OrderStateListener extends AbstractListener {
    private final FinamApi api;
    private final StreamProcessor<SubscribeOrdersResponse> processor;

    @SneakyThrows
    public OrderStateListener(NotificationService notificationService,
                              FinamApi api,
                              OrderStateObserver orderStateObserver
    ) {
        super(notificationService);
        this.api = api;

        processor = response -> response.getOrdersList()
                .forEach(orderState -> {
                    if (OrderStatus.ORDER_STATUS_FILLED.equals(orderState.getStatus())) {
                        log.info("process orderState {}", orderState);
                    }
                    orderStateObserver.onChangeOrderState(orderState);
                });
    }

    @PostConstruct
    @Override
    public void startToListen() {
        if (api.getOrdersStreamService().existOrderSubscription()) {
            return;
        }

        api.getOrdersStreamService().subscribeOrder(processor, this::onError, api.getAccountId());
    }

    @Override
    public void stopToListen() {
        api.getOrdersStreamService().cancelOrderSubscription();
    }

    @Override
    public String getPrefixError() {
        return "order state error";
    }
}
