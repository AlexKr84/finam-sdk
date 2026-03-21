package ru.finam.trade.core.stream;

public interface StreamProcessor<T> {

  void process(T response);
}
