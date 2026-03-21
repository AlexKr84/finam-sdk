package ru.finam.trade.hedging.order_state;

import grpc.tradeapi.v1.orders.OrderState;

public interface OrderStateObserver {
  void onChangeOrderState(OrderState orderState);
}
