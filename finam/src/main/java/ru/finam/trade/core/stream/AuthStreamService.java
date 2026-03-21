package ru.finam.trade.core.stream;

import grpc.tradeapi.v1.auth.AuthServiceGrpc;
import grpc.tradeapi.v1.auth.SubscribeJwtRenewalRequest;
import grpc.tradeapi.v1.auth.SubscribeJwtRenewalResponse;
import io.grpc.Context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

public class AuthStreamService {
    private final AuthServiceGrpc.AuthServiceStub stub;
    private volatile Context.CancellableContext context;

    public AuthStreamService(@Nonnull AuthServiceGrpc.AuthServiceStub stub) {
        this.stub = stub;
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
     * Подписка на стрим обновления токена
     *
     * @param streamProcessor обработчик пришедших сообщений в стриме
     * @param onErrorCallback обработчик ошибок в стриме
     * @param secret          секрет
     */
    public void subscribeJwtRenewal(@Nonnull StreamProcessor<SubscribeJwtRenewalResponse> streamProcessor,
                                    @Nullable Consumer<Throwable> onErrorCallback,
                                    @Nonnull String secret) {
        jwtRenewalStream(streamProcessor, onErrorCallback, secret);
    }

    /**
     * Подписка на стрим обновления токена
     *
     * @param streamProcessor обработчик пришедших сообщений в стриме
     * @param secret          секрет
     */
    public void subscribeJwtRenewal(@Nonnull StreamProcessor<SubscribeJwtRenewalResponse> streamProcessor,
                                    @Nonnull String secret) {
        jwtRenewalStream(streamProcessor, null, secret);
    }

    private void jwtRenewalStream(@Nonnull StreamProcessor<SubscribeJwtRenewalResponse> streamProcessor,
                                  @Nullable Consumer<Throwable> onErrorCallback,
                                  @Nonnull String secret) {
        var request = SubscribeJwtRenewalRequest
                .newBuilder()
                .setSecret(secret)
                .build();

        var context = Context.current().fork().withCancellation();
        this.context = context;
        context.run(() ->
                stub.subscribeJwtRenewal(
                        request,
                        new StreamObserverWithProcessor<>(streamProcessor, onErrorCallback)
                ));
    }
}
