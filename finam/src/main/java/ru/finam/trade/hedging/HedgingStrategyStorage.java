package ru.finam.trade.hedging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import grpc.tradeapi.v1.AccountTrade;
import grpc.tradeapi.v1.orders.OrderState;
import jakarta.annotation.PreDestroy;
import lombok.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.finam.trade.config.HedgingInstrument;
import ru.finam.trade.config.HedgingStrategyConfiguration;
import ru.finam.trade.hedging.diff.AdvancedHedgingDiff;
import ru.finam.trade.hedging.order_state.OrderStateObserver;
import ru.finam.trade.hedging.trade.TradeObserver;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class HedgingStrategyStorage implements OrderStateObserver, TradeObserver {
    private final Map<HedgingInstrument, List<HedgingStrategy>> strategyMap;
    private final Map<String, StrategyInfo> strategyInfoMap;
    @Getter
    private final List<String> symbolList;

    public HedgingStrategyStorage(Map<HedgingInstrument, List<HedgingStrategy>> strategyMap) {
        this.strategyMap = strategyMap;
        val hedgingStrategyList = strategyMap.values().stream()
                .flatMap(Collection::stream)
                .toList();
        symbolList = hedgingStrategyList.stream()
                .map(HedgingStrategy::getSymbolList)
                .flatMap(Collection::stream)
                .distinct()
                .toList();
        strategyInfoMap = symbolList.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        symbol -> new StrategyInfo(
                                hedgingStrategyList.stream()
                                        .filter(hedgingStrategy -> hedgingStrategy.getSymbolList().contains(symbol))
                                        .toList())
                ));
    }

    public void buyOrSell(String symbol) {
        val strategyInfo = strategyInfoMap.get(symbol);
        strategyInfo.activeStrategies.sort(Comparator.comparing(HedgingStrategy::getDiffToExpected));
        strategyInfo.activeStrategies.forEach(s -> s.buyOrSell(strategyInfo.inverseMode));
    }

    public void updateStrategyInfo(List<String> symbolList) {
        symbolList.stream()
                .map(strategyInfoMap::get)
                .forEach(StrategyInfo::update);
    }

    @PreDestroy
    public void save() {
        val instruments = new ArrayList<>();
        strategyMap.forEach((hedgingInstrument, hedgingStrategyList) ->
                hedgingStrategyList.stream()
                        .filter(HedgingStrategy::isAvailableForSell)
                        .forEach(hedgingStrategy -> instruments.add(
                                HedgingInstrument.of(
                                        hedgingInstrument,
                                        1,
                                        HedgingStrategyConfiguration.of(hedgingInstrument.strategy(), hedgingStrategy.getOrderDiff())))));
        Map<String, Object> instrumentsMap = new HashMap<>();
        instrumentsMap.put("instruments", instruments);
        Map<String, Object> data = new HashMap<>();
        data.put("hedging", instrumentsMap);
        save(data);
    }

    @SneakyThrows
    private void save(Map<String, Object> data) {
        val mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        mapper.registerModule(new JavaTimeModule());
        mapper.writeValue(new File("instruments.yaml"), data);
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void updateStrategy() {
        strategyMap.forEach((hedgingInstrument, hedgingStrategyList) -> {
            var diffToMax = hedgingInstrument.strategy().diffToMax();
            for (var hedgingStrategy : hedgingStrategyList) {
                hedgingStrategy.setPositionExited(false);
                hedgingStrategy.setFundingUpdated(false);
                if (hedgingStrategy.isAvailableForBuy()) {
                    hedgingStrategy.setDiffToMax(diffToMax);
                    diffToMax = diffToMax.add(hedgingInstrument.addToDiff());
                }
            }
        });
    }

    @Override
    public void onChangeOrderState(OrderState orderState) {
        Optional.ofNullable(strategyInfoMap.get(orderState.getOrder().getSymbol())).ifPresent(
                strategyInfo ->
                        strategyInfo.activeStrategies.forEach(strategy ->
                                strategy.onChangeOrderState(orderState)));
    }

    @Override
    public void onTrade(AccountTrade trade) {
        Optional.ofNullable(strategyInfoMap.get(trade.getSymbol())).ifPresent(
                strategyInfo ->
                        strategyInfo.activeStrategies.forEach(strategy -> {
                                    if (strategy.onTrade(trade)) {
                                        updateStrategyInfo(strategy.getSymbolList());
                                    }
                                }
                        ));
    }

    @Getter
    private static class StrategyInfo {
        private static final Set<Predicate<HedgingStrategy>> PREDICATES = Set.of(HedgingStrategy::isAvailableForBuy, HedgingStrategy::isAvailableForSell);

        private final List<HedgingStrategy> allStrategies;
        @Setter
        private List<HedgingStrategy> activeStrategies;
        @Setter
        private Boolean inverseMode;

        public StrategyInfo(List<HedgingStrategy> allStrategies) {
            this.allStrategies = allStrategies;
            update();
        }

        private void update() {
            Set<Predicate<HedgingStrategy>> predicates = new HashSet<>(PREDICATES);
            val strategyList = getAllStrategies().stream()
                    .sorted(Comparator.comparing(HedgingStrategy::getValue)
                            .thenComparing(HedgingStrategy::getDiffToMax))
                    .takeWhile(ignore -> !predicates.isEmpty())
                    .filter(hedgingStrategy -> {
                        for (var predicate : predicates) {
                            if (predicate.test(hedgingStrategy)) {
                                predicates.remove(predicate);
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
            val inverseMode = strategyList.stream()
                    .map(HedgingStrategy::getOrderDiff)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .map(AdvancedHedgingDiff::isInverse)
                    .orElse(null);
            setActiveStrategies(strategyList);
            setInverseMode(inverseMode);
        }
    }
}
