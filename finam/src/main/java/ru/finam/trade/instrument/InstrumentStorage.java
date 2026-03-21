package ru.finam.trade.instrument;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;
import ru.finam.trade.api.FinamApi;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class InstrumentStorage {
    private final FinamApi api;
    @Getter
    private final Map<String, InstrumentInfo> instruments = new HashMap<>();

    public InstrumentInfo getInstrument(String exchangeCode, String ticker) {
        val symbol = String.format(InstrumentInfo.SYMBOL_FORMAT, ticker, exchangeCode);
        return instruments.computeIfAbsent(
                symbol,
                s -> InstrumentInfo.of(api.getAssetsService().getAsset(s, api.getAccountId())));
    }
}
