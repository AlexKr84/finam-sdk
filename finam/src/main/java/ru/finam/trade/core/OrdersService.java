package ru.finam.trade.core;

import grpc.tradeapi.v1.Side;
import grpc.tradeapi.v1.orders.*;
import lombok.val;
import ru.finam.trade.common.DecimalUtils;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.Optional;


public class OrdersService extends CommonAuthorizedService {
    private final OrdersServiceGrpc.OrdersServiceStub ordersServiceStub;
    private final OrdersServiceGrpc.OrdersServiceBlockingStub ordersServiceBlockingStub;

    public OrdersService(TokenStorage tokenStorage, OrdersServiceGrpc.OrdersServiceStub ordersServiceStub, OrdersServiceGrpc.OrdersServiceBlockingStub ordersServiceBlockingStub) {
        super(tokenStorage);
        this.ordersServiceStub = ordersServiceStub;
        this.ordersServiceBlockingStub = ordersServiceBlockingStub;
    }

    public OrdersServiceGrpc.OrdersServiceStub getOrdersServiceStub() {
        return withAuthorized(ordersServiceStub);
    }

    public OrdersServiceGrpc.OrdersServiceBlockingStub getOrdersServiceBlockingStub() {
        return withAuthorized(ordersServiceBlockingStub);
    }

    public OrderState cancelOrder(@Nonnull String orderId, @Nonnull String accountId) {
        return getOrdersServiceBlockingStub().cancelOrder(
                CancelOrderRequest.newBuilder()
                        .setOrderId(orderId)
                        .setAccountId(accountId)
                        .build());
    }

    public OrderState getOrder(@Nonnull String orderId, @Nonnull String accountId) {
        return getOrdersServiceBlockingStub().getOrder(
                GetOrderRequest.newBuilder()
                        .setOrderId(orderId)
                        .setAccountId(accountId)
                        .build());
    }

    public OrderState placeOrder(@Nonnull String symbol,
                                 @Nonnull String accountId,
                                 @Nonnull BigDecimal quantity,
                                 @Nonnull Side side,
                                 @Nonnull OrderType type,
                                 @Nonnull TimeInForce timeInForce,
                                 BigDecimal limitPrice,
                                 BigDecimal stopPrice,
                                 StopCondition stopCondition,
                                 Iterable<Leg> legs,
                                 String clientOrderId,
                                 ValidBefore validBefore,
                                 String comment) {
        val builder = Order.newBuilder()
                .setSymbol(symbol)
                .setAccountId(accountId)
                .setQuantity(DecimalUtils.toDecimal(quantity))
                .setSide(side)
                .setType(type)
                .setTimeInForce(timeInForce);
        Optional.ofNullable(limitPrice).map(DecimalUtils::toDecimal).ifPresent(builder::setLimitPrice);
        Optional.ofNullable(stopPrice).map(DecimalUtils::toDecimal).ifPresent(builder::setStopPrice);
        Optional.ofNullable(stopCondition).ifPresent(builder::setStopCondition);
        Optional.ofNullable(legs).ifPresent(builder::addAllLegs);
        Optional.ofNullable(clientOrderId).ifPresent(builder::setClientOrderId);
        Optional.ofNullable(validBefore).ifPresent(builder::setValidBefore);
        Optional.ofNullable(comment).ifPresent(builder::setComment);
        return getOrdersServiceBlockingStub().placeOrder(builder.build());
    }
}
