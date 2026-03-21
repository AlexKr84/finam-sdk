package ru.finam.trade.instrument;

import grpc.tradeapi.v1.assets.GetAssetResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import ru.finam.trade.common.DateUtils;
import ru.finam.trade.common.DecimalUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;

@Builder
@Getter
@AllArgsConstructor
public class InstrumentInfo {
    public static final String SYMBOL_FORMAT = "%s@%s";

    private final String name;
    private final String symbol;
    private final String ticker;
    private final String instrumentType;
    private final ZonedDateTime lastTradeDate;
    private final int lotSize;
    private final BigDecimal basicAssetSize;
    private final BigDecimal minPriceIncrement;

    public static InstrumentInfo of(GetAssetResponse asset) {
        return InstrumentInfo.builder()
                .name(asset.getName())
                .symbol(String.format(SYMBOL_FORMAT, asset.getTicker(), asset.getMic()))
                .ticker(asset.getTicker())
                .instrumentType(asset.getType())
                .lastTradeDate(asset.hasExpirationDate() ? DateUtils.dateToZonedDateTime(asset.getExpirationDate()) : null)
                .lotSize("FUTURES".equals(asset.getType()) ? 1 : Objects.requireNonNull(DecimalUtils.toBigDecimal(asset.getLotSize())).intValue())
                .basicAssetSize("FUTURES".equals(asset.getType()) ? DecimalUtils.toBigDecimal(asset.getLotSize()) : null)
                .minPriceIncrement(BigDecimal.valueOf(asset.getMinStep() / Math.pow(10, asset.getDecimals())))
                .build();
    }
}
