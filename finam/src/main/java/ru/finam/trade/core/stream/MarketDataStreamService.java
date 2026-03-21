package ru.finam.trade.core.stream;

import grpc.tradeapi.v1.marketdata.MarketDataServiceGrpc;
import grpc.tradeapi.v1.marketdata.SubscribeQuoteRequest;
import grpc.tradeapi.v1.marketdata.SubscribeQuoteResponse;
import io.grpc.Context;
import ru.finam.trade.core.CommonAuthorizedService;
import ru.finam.trade.core.TokenStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

public class MarketDataStreamService extends CommonAuthorizedService {
    private final MarketDataServiceGrpc.MarketDataServiceStub stub;
    private volatile Context.CancellableContext context;

    public MarketDataStreamService(TokenStorage tokenStorage, @Nonnull MarketDataServiceGrpc.MarketDataServiceStub stub) {
        super(tokenStorage);
        this.stub = stub;
    }

    public MarketDataServiceGrpc.MarketDataServiceStub getStub() {
        return withAuthorized(stub);
    }

    public void cancelSubscription() {
        if (context != null) {
            context.cancel(new RuntimeException("canceled by user"));
        }
        context = null;
    }

    public boolean existSubscription() {
        return context != null;
    }

    /**
     * Подписка на стрим котировок
     *
     * @param streamProcessor обработчик пришедших сообщений в стриме
     * @param onErrorCallback обработчик ошибок в стриме
     * @param symbols         инструменты
     */
    public void subscribeQuote(@Nonnull StreamProcessor<SubscribeQuoteResponse> streamProcessor,
                               @Nullable Consumer<Throwable> onErrorCallback,
                               @Nonnull Iterable<String> symbols) {
        quoteStream(streamProcessor, onErrorCallback, symbols);
    }

    /**
     * Подписка на стрим котировок
     *
     * @param streamProcessor обработчик пришедших сообщений в стриме
     * @param symbols         инструменты
     */
    public void subscribeQuote(@Nonnull StreamProcessor<SubscribeQuoteResponse> streamProcessor,
                               @Nonnull Iterable<String> symbols) {
        quoteStream(streamProcessor, null, symbols);
    }

    private void quoteStream(@Nonnull StreamProcessor<SubscribeQuoteResponse> streamProcessor,
                             @Nullable Consumer<Throwable> onErrorCallback,
                             @Nonnull Iterable<String> symbols) {
        var request = SubscribeQuoteRequest
                .newBuilder()
                .addAllSymbols(symbols)
                .build();

        var context = Context.current().fork().withCancellation();
        this.context = context;
        context.run(() ->
                getStub().subscribeQuote(
                        request,
                        new StreamObserverWithProcessor<>(streamProcessor, onErrorCallback)
                ));
    }
}
