package ru.finam.trade.hedging.funding;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.config.HedgingInstrument;
import ru.finam.trade.instrument.InstrumentInfo;
import ru.finam.trade.instrument.InstrumentStorage;
import ru.finam.trade.notification.NotificationService;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HedgingFundingFactory {
    private final Map<String, HedgingFunding> hedgingFundingMap = new HashMap<>();
    private final FinamApi api;
    private final NotificationService notificationService;
    private final InstrumentStorage instrumentStorage;

    public HedgingFunding getHedgingFunding(HedgingInstrument instrument) {
        return hedgingFundingMap.computeIfAbsent(
                instrument.currentTicker(),
                ticker -> new HedgingFunding(api, notificationService, geInstrumentByTicker(ticker)));
    }

    private InstrumentInfo geInstrumentByTicker(String ticker) {
        return instrumentStorage.getInstrument("RTSX", ticker);
    }

    @Scheduled(cron = "0 30 1 * * *")
    public void updateFunding() {
        updateFunding(true);
    }

    public void updateFunding(boolean withFuturePayment) {
        hedgingFundingMap.values().forEach(hedgingFunding -> hedgingFunding.updateFunding(withFuturePayment));
    }
}
