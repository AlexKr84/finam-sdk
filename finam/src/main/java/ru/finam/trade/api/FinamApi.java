package ru.finam.trade.api;

import grpc.tradeapi.v1.Side;
import grpc.tradeapi.v1.orders.OrderState;
import grpc.tradeapi.v1.orders.OrderType;
import grpc.tradeapi.v1.orders.TimeInForce;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import ru.finam.trade.config.FinamApiConfiguration;
import ru.finam.trade.core.AccountsService;
import ru.finam.trade.core.AssetsService;
import ru.finam.trade.core.InvestApi;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;
import ru.finam.trade.core.MarketDataService;
import ru.finam.trade.core.stream.AuthStreamService;
import ru.finam.trade.core.stream.MarketDataStreamService;
import ru.finam.trade.core.stream.OrdersStreamService;
import ru.finam.trade.hedging.jwt.TokenObserver;
import ru.finam.trade.instrument.InstrumentInfo;

import java.math.BigDecimal;

@Service
@Slf4j
public class FinamApi implements TokenObserver {
    private final InvestApi api;
    @Getter
    private final String accountId;
    @Getter
    private final String secret;

    public FinamApi(
            FinamApiConfiguration finamApiConfiguration
    ) {
        secret = finamApiConfiguration.getSecret();
        accountId = finamApiConfiguration.getAccountId();
        api = InvestApi.create(secret);
    }

    @PreDestroy
    public void destroy() {
        api.destroy(3);
    }

    public AuthStreamService getAuthStreamService() {
        return api.getAuthStreamService();
    }

    public MarketDataStreamService getMarketDataStreamService() {
        return api.getMarketDataStreamService();
    }

    public MarketDataService getMarketDataService() {
        return api.getMarketDataService();
    }

    public OrdersStreamService getOrdersStreamService() {
        return api.getOrdersStreamService();
    }

    public AssetsService getAssetsService() {
        return api.getAssetsService();
    }

    public AccountsService getAccountsService() {
        return api.getAccountsService();
    }

    public final OrderResult postOrder(InstrumentInfo instrument, BigDecimal price, Integer count, Side side, OrderType orderType) {
        val quantity = BigDecimal.valueOf(count / instrument.getLotSize());
        BigDecimal normalizedPrice = null;
        if (price != null) {
            val minPriceIncrement = instrument.getMinPriceIncrement();
            normalizedPrice = price.divideToIntegralValue(minPriceIncrement).multiply(minPriceIncrement);
        }
        val result = api.getOrdersService().placeOrder(instrument.getSymbol(), accountId, quantity, side, orderType,
                TimeInForce.TIME_IN_FORCE_DAY, normalizedPrice, null, null, null, null, null, null);
        log.info("Send placeOrder with: symbol {}, quantity {}, price {}, side {}, acc {}, type {}, id {}",
                instrument.getSymbol(), quantity, normalizedPrice, side, accountId, orderType, result.getOrderId());

        return OrderResult.builder()
                .symbol(result.getOrder().getSymbol())
                .orderId(result.getOrderId())
                .status(result.getStatus())
                .build();
    }

    public void cancelOrder(String orderId) {
        log.info("Send cancelOrder with: orderId {}, acc {}",
                orderId, accountId);
        api.getOrdersService().cancelOrder(orderId, accountId);
    }

    public OrderState getOrderState(String orderId) {
        log.info("Send getOrder with: orderId {}, acc {}",
                orderId, accountId);
        return api.getOrdersService().getOrder(orderId, accountId);
    }

    @Override
    public void onToken(String token) {
        api.onToken(token);
    }
}
