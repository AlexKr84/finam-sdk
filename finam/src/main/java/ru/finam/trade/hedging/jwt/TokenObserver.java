package ru.finam.trade.hedging.jwt;

public interface TokenObserver {
  void onToken(String token);
}
