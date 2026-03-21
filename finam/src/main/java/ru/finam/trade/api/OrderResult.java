package ru.finam.trade.api;

import grpc.tradeapi.v1.orders.OrderStatus;
import lombok.Builder;

@Builder
public record OrderResult(
        String symbol,
        String orderId,
        OrderStatus status
) {
}
