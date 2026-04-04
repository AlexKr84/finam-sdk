package ru.finam.trade.hedging;

import grpc.tradeapi.v1.AccountTrade;
import grpc.tradeapi.v1.Side;
import grpc.tradeapi.v1.orders.OrderState;
import grpc.tradeapi.v1.orders.OrderStatus;
import grpc.tradeapi.v1.orders.OrderType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.DateUtils;
import ru.finam.trade.common.DecimalUtils;
import ru.finam.trade.config.HedgingStrategyConfiguration;
import ru.finam.trade.hedging.diff.AdvancedHedgingDiff;
import ru.finam.trade.hedging.diff.HedgingDiff;
import ru.finam.trade.hedging.funding.FundingObserver;
import ru.finam.trade.hedging.funding.PositionInfo;
import ru.finam.trade.hedging.order_state.OrderStateObserver;
import ru.finam.trade.instrument.InstrumentInfo;
import ru.finam.trade.notification.NotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class HedgingStrategy implements OrderStateObserver, FundingObserver {
    public static final LocalTime START_TIME_MARKET = LocalTime.parse("09:01:00");
    private static final BigDecimal DIFF_TO_FUTURE_ORDER = BigDecimal.valueOf(3);

    private final FinamApi api;
    private final NotificationService notificationService;
    private final HedgingStats hedgingStats;
    private final int currentPositionSize;
    private final int futurePositionSize;
    private final boolean withExitPosition;
    private final boolean sellFuture;
    private final BigDecimal addToRemainingDays;
    private final BigDecimal needDiff;
    @Getter
    @Setter
    private BigDecimal diffToMax;
    private final BigDecimal diffToMin;
    private final BigDecimal needDiffInverse;
    private final BigDecimal diffToMaxInverse;
    private final BigDecimal diffToMinInverse;
    @Getter
    private volatile AdvancedHedgingDiff orderDiff;
    private final AtomicReference<Order> futureOrder = new AtomicReference<>();
    private volatile Order currentOrder;
    private volatile BigDecimal expectedDiff;
    @Setter
    private volatile boolean positionExited;

    public HedgingStrategy(FinamApi api,
                           NotificationService notificationService,
                           HedgingStats hedgingStats,
                           HedgingStrategyConfiguration strategyConfiguration) {
        this.hedgingStats = hedgingStats;
        this.api = api;
        this.notificationService = notificationService;
        withExitPosition = !strategyConfiguration.sellFuture() && strategyConfiguration.withExitPosition();
        sellFuture = strategyConfiguration.sellFuture();
        addToRemainingDays = strategyConfiguration.addToRemainingDays();
        needDiff = strategyConfiguration.needDiff();
        diffToMax = strategyConfiguration.diffToMax();
        diffToMin = strategyConfiguration.diffToMin();
        needDiffInverse = strategyConfiguration.needDiffInverse();
        diffToMaxInverse = strategyConfiguration.diffToMaxInverse();
        diffToMinInverse = strategyConfiguration.diffToMinInverse();
        currentPositionSize = strategyConfiguration.currentPositionSize();
        futurePositionSize = strategyConfiguration.futurePositionSize();
        val orderDiff = strategyConfiguration.orderDiff();
        this.orderDiff = orderDiff != null ?
                AdvancedHedgingDiff.builder()
                        .currentPrice(orderDiff.currentPrice())
                        .futurePrice(orderDiff.futurePrice())
                        .multiplier(hedgingStats.getMultiplier())
                        .currentVolume(orderDiff.currentVolume())
                        .futureVolume(orderDiff.futureVolume())
                        .date(orderDiff.date())
                        .isInverse(orderDiff.isInverse())
                        .build() : null;
        currentOrder = null;
        positionExited = false;
    }

    public List<String> getSymbolList() {
        return List.of(hedgingStats.getFutureSymbol(), hedgingStats.getCurrentSymbol());
    }

    public boolean isAvailableForBuy() {
        return orderDiff == null && !positionExited;
    }

    public boolean isAvailableForSell() {
        return orderDiff != null;
    }

    public BigDecimal getDiffToExpected() {
        return Optional.ofNullable(expectedDiff)
                .map(e -> e.subtract(hedgingStats.getDiffStats().getLastDiff().getValue()).abs())
                .orElse(BigDecimal.ZERO);
    }

    public void buyOrSell(Boolean inverseMode) {
        if (!isStartTradeMarket()) {
            return;
        }
        val diff = hedgingStats.getDiffStats().getLastDiff();
        if (diff.getValue() == null) {
            return;
        }

        buyOrSellFutureInstrument(diff.getNormalizedCurrentPrice(), diff.getFuturePrice(), inverseMode);
    }

    private void buyOrSellFutureInstrument(BigDecimal normalizedCurrentPrice, BigDecimal futurePrice, Boolean inverseMode) {
        Side side = null;
        val contangoDay = hedgingStats.getContangoStats().contangoDay();
        int futurePositionSize = 0;
        if (orderDiff == null && !positionExited) {
            if (withExitPosition) {
                val diffStats = hedgingStats.getDiffStats();
                val maxDiff = diffStats.getMaxDiff();
                val maxDiffToMax = diffStats.getMaxDiffToMax();
                val minDiff = diffStats.getMinDiff();
                val maxDiffToMin = diffStats.getMaxDiffToMin();
                val lastDiff = diffStats.getLastDiff().getValue();
                val buyExpectedDiff = maxDiffToMin.compareTo(diffToMin) > 0 ? maxDiff.subtract(diffToMax) : null;
                val sellExpectedDiff = maxDiffToMax.compareTo(diffToMaxInverse) > 0 ? minDiff.add(diffToMinInverse) : null;
                val isInverse = inverseMode != null ? inverseMode : buyExpectedDiff == null
                        || sellExpectedDiff != null && lastDiff.subtract(buyExpectedDiff).abs().compareTo(lastDiff.subtract(sellExpectedDiff).abs()) > 0;
                if (isInverse) {
                    expectedDiff = sellExpectedDiff;
                    side = Side.SIDE_SELL;
                } else {
                    expectedDiff = buyExpectedDiff;
                    side = Side.SIDE_BUY;
                }
            } else {
                if (needDiffInverse != null) {
                    expectedDiff = needDiffInverse;
                    side = Side.SIDE_SELL;
                } else {
                    expectedDiff = needDiff != null ? needDiff : contangoDay.multiply(hedgingStats.getRemainingDays().add(addToRemainingDays));
                    side = Side.SIDE_BUY;
                }
            }
            futurePositionSize = this.futurePositionSize;
        } else if (orderDiff != null && withExitPosition) {
            if (orderDiff.isInverse()) {
                expectedDiff = orderDiff.getValue().subtract(needDiffInverse);
                side = Side.SIDE_BUY;
            } else {
                expectedDiff = orderDiff.getValue().add(needDiff);
                side = Side.SIDE_SELL;
            }
            futurePositionSize = orderDiff.getFutureVolume();
        }

        if (side != null && expectedDiff != null) {
            val expectedFuturePrice = calcExpectedFuturePrice(normalizedCurrentPrice, expectedDiff);
            if (futurePrice.subtract(expectedFuturePrice).abs().compareTo(contangoDay.multiply(DIFF_TO_FUTURE_ORDER)) < 0) {
                postFutureOrder(expectedFuturePrice, side, futurePositionSize);
                return;
            }
        }
        cancelFutureOrder();
    }

    private BigDecimal calcExpectedFuturePrice(BigDecimal normalizedCurrentPrice, BigDecimal expectedDiff) {
        return normalizedCurrentPrice.add(expectedDiff);
    }

    private void postFutureOrder(BigDecimal futurePrice, Side side, int futurePositionSize) {
        val contangoDay = hedgingStats.getContangoStats().contangoDay();
        if (Side.SIDE_BUY.equals(side)) {
            futurePrice = futurePrice.subtract(contangoDay);
        } else {
            futurePrice = futurePrice.add(contangoDay);
        }
        val futureOrder = this.futureOrder.get();
        if (futureOrder != null && futurePrice.subtract(futureOrder.price).abs().compareTo(contangoDay) < 0) {
            return;
        }

        if (!cancelFutureOrder()) {
            return;
        }
        synchronized (this) {
            if (this.futureOrder.get() == null) {
                val futureResult = api.postOrder(
                        hedgingStats.getFutureInstrument(), futurePrice, futurePositionSize, side, OrderType.ORDER_TYPE_LIMIT);
                this.futureOrder.set(new Order(futureResult.orderId(), futureResult.symbol(), futurePrice, futureResult.status(), futurePositionSize));
            }
        }
    }

    private boolean cancelFutureOrder() {
        val futureOrder = this.futureOrder.get();
        if (futureOrder == null) {
            return true;
        }
        synchronized (futureOrder) {
            if (futureOrder.status.equals(OrderStatus.ORDER_STATUS_NEW)) {
                futureOrder.status = api.getOrderState(futureOrder.orderId).getStatus();
            }
        }
        if (!OrderStatus.ORDER_STATUS_NEW.equals(futureOrder.status)) {
            return false;
        }

        try {
            api.cancelOrder(futureOrder.orderId);
            return this.futureOrder.compareAndSet(futureOrder, null);
        } catch (Throwable error) {
            log.error("cancel order error", error);
            return false;
        }
    }

    private static boolean isStartTradeMarket() {
        return DateUtils.now().toLocalTime().isAfter(START_TIME_MARKET);
    }

    @Override
    public synchronized void onChangeOrderState(OrderState orderState) {
        val futureOrder = this.futureOrder.get();
        if (futureOrder != null && futureOrder.orderId.equals(orderState.getOrderId()) && currentOrder == null) {
            log.info("change order state {} with future order {}", orderState, futureOrder);
            val status = orderState.getStatus();

            if (OrderStatus.ORDER_STATUS_PARTIALLY_FILLED.equals(status)) {
                try {
                    api.cancelOrder(futureOrder.orderId);
                } catch (Throwable error) {
                    log.error("cancel order error", error);
                }
                return;
            }
            if (OrderStatus.ORDER_STATUS_FILLED.equals(status) || OrderStatus.ORDER_STATUS_CANCELED.equals(status)) {
                val lotsExecuted = Objects.requireNonNull(DecimalUtils.toBigDecimal(orderState.getExecutedQuantity())).intValue();
                if (lotsExecuted == 0) {
                    return;
                }
                futureOrder.lotsExecuted = lotsExecuted;
                if (orderDiff != null) {
                    orderDiff.setFutureVolume(orderDiff.getFutureVolume() - lotsExecuted);
                }
                val diff = hedgingStats.getDiffStats().getLastDiff();
                if (!sellFuture) {
                    val currentPositionSize = lotsExecuted * this.currentPositionSize / futurePositionSize;
                    val side = orderState.getOrder().getSide().equals(Side.SIDE_BUY) ? Side.SIDE_SELL : Side.SIDE_BUY;
                    buyOrSellCurrentInstrument(diff.getNormalizedCurrentPrice(), currentPositionSize, side);
                } else {
                    val anotherFuturePrice = diff.getFuturePrice().subtract(hedgingStats.getContangoStats().contango());
                    sellAnotherFutureInstrument(anotherFuturePrice, lotsExecuted);
                }
            }
        }
    }

    private void buyOrSellCurrentInstrument(BigDecimal currentPrice, int currentPositionSize, Side side) {
        postSecondInstrument(
                hedgingStats.getCurrentInstrument(), currentPrice, currentPositionSize, side);
    }

    private void sellAnotherFutureInstrument(BigDecimal anotherFuturePrice, int anotherFuturePositionSize) {
        postSecondInstrument(
                hedgingStats.getAnotherFutureInstrument(), anotherFuturePrice, anotherFuturePositionSize, Side.SIDE_SELL);
    }

    private void postSecondInstrument(InstrumentInfo instrumentInfo, BigDecimal price, int positionSize, Side side) {
        val currentResult = api.postOrder(
                instrumentInfo, null, positionSize, side, OrderType.ORDER_TYPE_MARKET);
        currentOrder = new Order(currentResult.orderId(), currentResult.symbol(), price, currentResult.status(), positionSize);
    }

    public synchronized boolean onTrade(AccountTrade trade) {
        if (currentOrder != null && currentOrder.orderId.equals(trade.getOrderId())) {
            val currentPrice = Objects.requireNonNull(DecimalUtils.toBigDecimal(trade.getPrice()));
            val size = Objects.requireNonNull(DecimalUtils.toBigDecimal(trade.getSize()));
            val lotsExecuted = BigDecimal.valueOf(currentOrder.lotsExecuted);
            currentOrder.lotsExecuted += size.intValue();
            currentOrder.price = currentOrder.price.multiply(lotsExecuted)
                    .add(currentPrice.multiply(size))
                    .divide(BigDecimal.valueOf(currentOrder.lotsExecuted), 4, RoundingMode.HALF_UP);
            if (currentOrder.lotsExecuted != currentOrder.lotsRequested) {
                return false;
            }
            currentOrder.price = currentOrder.price.setScale(2, RoundingMode.HALF_UP);
            val futureOrder = this.futureOrder.get();
            if (orderDiff != null) {
                orderDiff.setCurrentVolume(orderDiff.getCurrentVolume() - currentOrder.lotsExecuted);
            } else {
                orderDiff = AdvancedHedgingDiff.builder()
                        .currentPrice(currentOrder.price)
                        .futurePrice(futureOrder.price)
                        .multiplier(hedgingStats.getMultiplier())
                        .futureVolume(futureOrder.lotsExecuted)
                        .currentVolume(currentOrder.lotsExecuted)
                        .date(DateUtils.now())
                        .isInverse(Side.SIDE_BUY.equals(trade.getSide()))
                        .build();
            }
            String futureDir = "buy";
            String resultDir = "sell";
            if (Side.SIDE_BUY.equals(trade.getSide())) {
                futureDir = "sell";
                resultDir = "buy";
            }
            val diff = new HedgingDiff(currentOrder.price, futureOrder.price, hedgingStats.getMultiplier());
            notificationService.sendMessage(String.format("%s %s = %.2f, %s %s = %.2f, value = %.2f, size = %d, inverse = %b",
                    futureDir, futureOrder.symbol, diff.getFuturePrice(),
                    resultDir, currentOrder.symbol, diff.getCurrentPrice(),
                    diff.getValue(),
                    currentOrder.lotsExecuted,
                    orderDiff.isInverse()));
            this.futureOrder.set(null);
            currentOrder = null;
            expectedDiff = null;
            if (orderDiff.getCurrentVolume() == 0) {
                orderDiff = null;
                positionExited = true;
            }
            return true;
        }
        return false;
    }

    @Override
    public void onUpdateFunding(LocalDate date, BigDecimal funding) {
        if (orderDiff != null && date.equals(orderDiff.getDate().toLocalDate())) {
            orderDiff.setFunding(funding);
        }
    }

    @Override
    public PositionInfo getCurrentPosition() {
        if (orderDiff == null) {
            return null;
        }
        return new PositionInfo(orderDiff.isInverse() ? orderDiff.getCurrentVolume() : -orderDiff.getCurrentVolume(), orderDiff.getCurrentPrice(), orderDiff.getDate());
    }

    public BigDecimal getValue() {
        return Optional.ofNullable(orderDiff)
                .map(o -> o.isInverse() ? o.getValue().negate() : o.getValue())
                .orElse(BigDecimal.ZERO);
    }

    private static class Order {
        private final String orderId;
        private final String symbol;
        private volatile BigDecimal price;
        private volatile OrderStatus status;
        private final int lotsRequested;
        private volatile int lotsExecuted;

        public Order(String orderId, String symbol, BigDecimal price, OrderStatus status, int lotsRequested) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.price = price;
            this.status = status;
            this.lotsRequested = lotsRequested;
            lotsExecuted = 0;
        }
    }
}
