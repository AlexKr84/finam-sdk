package ru.finam.trade.hedging.trade;

import grpc.tradeapi.v1.AccountTrade;

public interface TradeObserver {
  void onTrade(AccountTrade trade);
}
