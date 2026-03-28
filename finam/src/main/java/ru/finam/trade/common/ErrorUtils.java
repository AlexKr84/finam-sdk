package ru.finam.trade.common;

import lombok.experimental.UtilityClass;

import java.time.LocalTime;

@UtilityClass
public class ErrorUtils {
    private static final LocalTime START_TIME_MARKET = LocalTime.parse("07:00:00");

    public static boolean isNeedLoggingError(Throwable error) {
        return (error.getLocalizedMessage() == null || !error.getLocalizedMessage().contains("FIX server is offline"))
                && isStartTradeMarket();
    }

    private static boolean isStartTradeMarket() {
        return DateUtils.now().toLocalTime().isAfter(START_TIME_MARKET);
    }
}
