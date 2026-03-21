package ru.finam.trade.hedging.last_price;


import grpc.tradeapi.v1.marketdata.Quote;

public interface LastPriceObserver {
  void onChange(Quote quote);
}
