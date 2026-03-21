package ru.finam.trade.core;

import grpc.tradeapi.v1.accounts.AccountsServiceGrpc;
import grpc.tradeapi.v1.assets.AssetsServiceGrpc;
import grpc.tradeapi.v1.auth.AuthServiceGrpc;
import grpc.tradeapi.v1.marketdata.MarketDataServiceGrpc;
import grpc.tradeapi.v1.orders.OrdersServiceGrpc;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.ChannelOption;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.finam.trade.core.stream.AuthStreamService;
import ru.finam.trade.core.stream.MarketDataStreamService;
import ru.finam.trade.core.stream.OrdersStreamService;
import ru.finam.trade.hedging.jwt.TokenObserver;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Интерфейс API реальной торговли.
 * <p>
 * При использовании токена с правами только на чтение
 * вызов модифицирующих методов приводит к ошибке.
 */
@Slf4j
public class InvestApi implements TokenStorage, TokenObserver {

    private static final String configResourceName = "config.properties";
    private static final Properties props;

    static {
        props = loadProps();
    }

    @Getter
    private final Channel channel;
    @Getter
    private final AuthService authService;
    @Getter
    private final AuthStreamService authStreamService;
    @Getter
    private final AccountsService accountsService;
    @Getter
    private final MarketDataStreamService marketDataStreamService;
    @Getter
    private final MarketDataService marketDataService;
    @Getter
    private final OrdersStreamService ordersStreamService;
    @Getter
    private final OrdersService ordersService;
    @Getter
    private final AssetsService assetsService;
    @Getter
    private String token;

    private InvestApi(@Nonnull Channel channel, String secret) {
        this.channel = channel;
        val authStub = AuthServiceGrpc.newStub(channel);
        authService = new AuthService(
                authStub,
                AuthServiceGrpc.newBlockingStub(channel));
        authStreamService = new AuthStreamService(authStub);
        token = authService.auth(secret);
        accountsService = new AccountsService(
                this,
                AccountsServiceGrpc.newStub(channel),
                AccountsServiceGrpc.newBlockingStub(channel));
        val marketDataSub = MarketDataServiceGrpc.newStub(channel);
        marketDataStreamService = new MarketDataStreamService(
                this,
                marketDataSub);
        marketDataService = new MarketDataService(
                this,
                marketDataSub,
                MarketDataServiceGrpc.newBlockingStub(channel));
        val ordersStub = OrdersServiceGrpc.newStub(channel);
        ordersStreamService = new OrdersStreamService(
                this,
                ordersStub);
        ordersService = new OrdersService(
                this,
                ordersStub,
                OrdersServiceGrpc.newBlockingStub(channel));
        assetsService = new AssetsService(
                this,
                AssetsServiceGrpc.newStub(channel),
                AssetsServiceGrpc.newBlockingStub(channel));
    }

    /**
     * Создаёт экземпляр API для реальной торговли с использованием
     * готовой конфигурации GRPC-соединения.
     * <p>
     *
     * @param secret Токен для торговли.
     * @return Экземпляр API для реальной торговли.
     */
    @Nonnull
    public static InvestApi create(@Nonnull String secret) {
        return new InvestApi(defaultChannel(), secret);
    }

    @Nonnull
    public static Channel defaultChannel(String target) {
        val connectionTimeout = Duration.parse(props.getProperty("ru.finam.core.connection-timeout"));
        val requestTimeout = Duration.parse(props.getProperty("ru.finam.core.request-timeout"));

        return NettyChannelBuilder
                .forTarget(target)
                .intercept(
                        new LoggingInterceptor(),
                        new TimeoutInterceptor(requestTimeout))
                .withOption(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) connectionTimeout.toMillis()) // Намерено сужаем тип - предполагается,
                // что таймаут имеет разумную величину.
                .useTransportSecurity()
                .keepAliveTimeout(60, TimeUnit.SECONDS)
                .maxInboundMessageSize(16777216) // 16 Mb
                .build();
    }

    @Nonnull
    public static Channel defaultChannel() {
        var target = props.getProperty("ru.finam.core.api.target");
        return defaultChannel(target);
    }

    private static Properties loadProps() {
        var loader = Thread.currentThread().getContextClassLoader();
        var props = new Properties();
        try (var resourceStream = loader.getResourceAsStream(configResourceName)) {
            props.load(resourceStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props;
    }

    /**
     * Остановка подключение к api
     *
     * @param waitChannelTerminationSec - ожидание терминирования канала сек
     */
    public void destroy(int waitChannelTerminationSec) {
        try {
            ((ManagedChannel) getChannel())
                    .shutdownNow()
                    .awaitTermination(waitChannelTerminationSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onToken(String token) {
        this.token = token;
    }

    static class TimeoutInterceptor implements ClientInterceptor {
        private final Duration timeout;

        public TimeoutInterceptor(Duration timeout) {
            this.timeout = timeout;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            if (method.getType() == MethodDescriptor.MethodType.UNARY) {
                callOptions = callOptions.withDeadlineAfter(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
            }

            return next.newCall(method, callOptions);
        }
    }

    static class LoggingInterceptor implements ClientInterceptor {

        private final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new LoggingClientCall<>(
                    next.newCall(method, callOptions), logger, method);
        }
    }

    static class LoggingClientCall<ReqT, RespT>
            extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {

        private final Logger logger;
        private final MethodDescriptor<ReqT, RespT> method;

        LoggingClientCall(
                ClientCall<ReqT, RespT> call,
                Logger logger,
                MethodDescriptor<ReqT, RespT> method) {
            super(call);
            this.logger = logger;
            this.method = method;
        }

        @Override
        public void start(ClientCall.Listener<RespT> responseListener, Metadata headers) {
            logger.debug(
                    "Готовится вызов метода {} сервиса {}.",
                    method.getBareMethodName(),
                    method.getServiceName());
            super.start(
                    new LoggingClientCallListener<>(responseListener, logger, method),
                    headers);
        }
    }

    static class LoggingClientCallListener<RespT>
            extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

        private static final Metadata.Key<String> trackingIdKey =
                Metadata.Key.of("x-tracking-id", Metadata.ASCII_STRING_MARSHALLER);

        private final Logger logger;
        private final MethodDescriptor<?, RespT> method;
        volatile private String lastTrackingId;

        LoggingClientCallListener(
                ClientCall.Listener<RespT> listener,
                Logger logger,
                MethodDescriptor<?, RespT> method) {
            super(listener);
            this.logger = logger;
            this.method = method;
        }

        @Override
        public void onHeaders(Metadata headers) {
            lastTrackingId = headers.get(trackingIdKey);
            delegate().onHeaders(headers);
        }

        @Override
        public void onMessage(RespT message) {
            if (method.getType() == MethodDescriptor.MethodType.UNARY) {
                logger.debug(
                        "Пришёл ответ от метода {} сервиса {}. (x-tracking-id = {})",
                        method.getBareMethodName(),
                        method.getServiceName(),
                        lastTrackingId);
            } else {
                logger.debug(
                        "Пришло сообщение от потока {} сервиса {}.",
                        method.getBareMethodName(),
                        method.getServiceName());
            }

            delegate().onMessage(message);
        }
    }
}
