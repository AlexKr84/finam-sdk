package ru.finam.trade.core.stream;

import grpc.tradeapi.v1.orders.*;
import io.grpc.Context;
import ru.finam.trade.core.CommonAuthorizedService;
import ru.finam.trade.core.TokenStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

public class OrdersStreamService extends CommonAuthorizedService {
    private final OrdersServiceGrpc.OrdersServiceStub stub;
    private volatile Context.CancellableContext orderContext;
    private volatile Context.CancellableContext tradeContext;

    public OrdersStreamService(TokenStorage tokenStorage, @Nonnull OrdersServiceGrpc.OrdersServiceStub stub) {
        super(tokenStorage);
        this.stub = stub;
    }

    public OrdersServiceGrpc.OrdersServiceStub getStub() {
        return withAuthorized(stub);
    }

    public void cancelOrderSubscription() {
        if (orderContext != null) {
            orderContext.cancel(new RuntimeException("canceled by user"));
        }
        orderContext = null;
    }

    public boolean existOrderSubscription() {
        return orderContext != null;
    }

    /**
     * Подписка на стрим заявок
     *
     * @param streamProcessor обработчик пришедших сообщений в стриме
     * @param onErrorCallback обработчик ошибок в стриме
     * @param accountId       Идентификатор аккаунта
     */
    public void subscribeOrder(@Nonnull StreamProcessor<SubscribeOrdersResponse> streamProcessor,
                               @Nullable Consumer<Throwable> onErrorCallback,
                               @Nonnull String accountId) {
        orderStream(streamProcessor, onErrorCallback, accountId);
    }

    /**
     * Подписка на стрим заявок
     *
     * @param streamProcessor обработчик пришедших сообщений в стриме
     * @param accountId       Идентификатор аккаунта
     */
    public void subscribeOrder(@Nonnull StreamProcessor<SubscribeOrdersResponse> streamProcessor,
                               @Nonnull String accountId) {
        orderStream(streamProcessor, null, accountId);
    }

    private void orderStream(@Nonnull StreamProcessor<SubscribeOrdersResponse> streamProcessor,
                             @Nullable Consumer<Throwable> onErrorCallback,
                             @Nonnull String accountId) {
        var request = SubscribeOrdersRequest
                .newBuilder()
                .setAccountId(accountId)
                .build();

        var context = Context.current().fork().withCancellation();
        this.orderContext = context;
        context.run(() ->
                getStub().subscribeOrders(
                        request,
                        new StreamObserverWithProcessor<>(streamProcessor, onErrorCallback)
                ));
    }

    public void cancelTradeSubscription() {
        if (tradeContext != null) {
            tradeContext.cancel(new RuntimeException("canceled by user"));
        }
        tradeContext = null;
    }

    public boolean existTradeSubscription() {
        return tradeContext != null;
    }

    /**
     * Подписка на стрим сделок
     *
     * @param streamProcessor обработчик пришедших сообщений в стриме
     * @param onErrorCallback обработчик ошибок в стриме
     * @param accountId       Идентификатор аккаунта
     */
    public void subscribeTrade(@Nonnull StreamProcessor<SubscribeTradesResponse> streamProcessor,
                               @Nullable Consumer<Throwable> onErrorCallback,
                               @Nonnull String accountId) {
        tradeStream(streamProcessor, onErrorCallback, accountId);
    }

    /**
     * Подписка на стрим сделок
     *
     * @param streamProcessor обработчик пришедших сообщений в стриме
     * @param accountId       Идентификатор аккаунта
     */
    public void subscribeTrade(@Nonnull StreamProcessor<SubscribeTradesResponse> streamProcessor,
                               @Nonnull String accountId) {
        tradeStream(streamProcessor, null, accountId);
    }

    private void tradeStream(@Nonnull StreamProcessor<SubscribeTradesResponse> streamProcessor,
                             @Nullable Consumer<Throwable> onErrorCallback,
                             @Nonnull String accountId) {
        var request = SubscribeTradesRequest
                .newBuilder()
                .setAccountId(accountId)
                .build();

        var context = Context.current().fork().withCancellation();
        this.tradeContext = context;
        context.run(() ->
                getStub().subscribeTrades(
                        request,
                        new StreamObserverWithProcessor<>(streamProcessor, onErrorCallback)
                ));
    }
}
