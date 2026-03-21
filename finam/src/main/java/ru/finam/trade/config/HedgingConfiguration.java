package ru.finam.trade.config;

import io.smallrye.mutiny.tuples.Tuple2;
import lombok.Data;
import lombok.val;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.ListUtils;
import ru.finam.trade.hedging.HedgingStats;
import ru.finam.trade.hedging.HedgingStrategy;
import ru.finam.trade.hedging.funding.HedgingFundingFactory;
import ru.finam.trade.instrument.InstrumentStorage;
import ru.finam.trade.notification.NotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@ConfigurationProperties(prefix = "hedging")
@ConditionalOnProperty(name = "hedging.enabled", havingValue = "true")
@Data
public class HedgingConfiguration {
    private List<HedgingInstrument> instruments;

    @Bean
    public Map<String, HedgingStats> createHedgingStatsMap(FinamApi api,
                                                           InstrumentStorage instrumentStorage,
                                                           NotificationService notificationService) {
        return instruments.stream()
                .filter(ListUtils.distinctByKey(HedgingInstrument::getKey))
                .collect(Collectors.toMap(
                        HedgingInstrument::getKey,
                        instrument -> new HedgingStats(api, notificationService, instrumentStorage, instrument)));
    }

    @Bean
    public Map<HedgingInstrument, List<HedgingStrategy>> createHedgingStrategyMap(FinamApi api,
                                                                                  NotificationService notificationService,
                                                                                  Map<String, HedgingStats> hedgingStatsMap,
                                                                                  HedgingFundingFactory hedgingFundingFactory) {
        val res = instruments.stream()
                .map(instrument -> {
                            val strategyList = Stream.iterate(instrument.strategy().diffToMax(), diffToMax -> diffToMax.add(instrument.addToDiff()))
                                    .limit(instrument.instanceCount())
                                    .map(diffToMax -> new HedgingStrategy(
                                            api, notificationService,
                                            hedgingStatsMap.get(instrument.getKey()),
                                            HedgingStrategyConfiguration.of(instrument.strategy(), diffToMax)))
                                    .toList();
                            val funding = hedgingFundingFactory.getHedgingFunding(instrument);
                            funding.addFundingObservers(strategyList);
                            return Tuple2.of(instrument, strategyList);
                        }
                )
                .collect(Collectors.toMap(
                        Tuple2::getItem1,
                        Tuple2::getItem2,
                        ListUtils::concat
                        ));
        hedgingFundingFactory.updateFunding(false);
        return res;
    }
}
