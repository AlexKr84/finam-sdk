package ru.finam.trade.core;

import com.google.protobuf.Timestamp;
import com.google.type.Interval;
import grpc.tradeapi.v1.marketdata.*;

import javax.annotation.Nonnull;
import java.util.List;


public class MarketDataService extends CommonAuthorizedService {
    private final MarketDataServiceGrpc.MarketDataServiceStub marketDataServiceStub;
    private final MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataServiceBlockingStub;

    public MarketDataService(TokenStorage tokenStorage, MarketDataServiceGrpc.MarketDataServiceStub marketDataServiceStub, MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataServiceBlockingStub) {
        super(tokenStorage);
        this.marketDataServiceStub = marketDataServiceStub;
        this.marketDataServiceBlockingStub = marketDataServiceBlockingStub;
    }

    public MarketDataServiceGrpc.MarketDataServiceStub getMarketDataServiceStub() {
        return withAuthorized(marketDataServiceStub);
    }

    public MarketDataServiceGrpc.MarketDataServiceBlockingStub getMarketDataServiceBlockingStub() {
        return withAuthorized(marketDataServiceBlockingStub);
    }

    public List<Bar> getBars(@Nonnull String symbol,
                             @Nonnull TimeFrame timeFrame,
                             @Nonnull Timestamp startTime,
                             @Nonnull Timestamp endTime) {
        return getMarketDataServiceBlockingStub().bars(
                        BarsRequest.newBuilder()
                                .setSymbol(symbol)
                                .setTimeframe(timeFrame)
                                .setInterval(Interval.newBuilder()
                                        .setStartTime(startTime)
                                        .setEndTime(endTime)
                                        .build())
                                .build())
                .getBarsList();
    }

    public Quote getLastQuote(@Nonnull String symbol) {
        return getMarketDataServiceBlockingStub().lastQuote(
                        QuoteRequest.newBuilder()
                                .setSymbol(symbol)
                                .build())
                .getQuote();
    }
}
