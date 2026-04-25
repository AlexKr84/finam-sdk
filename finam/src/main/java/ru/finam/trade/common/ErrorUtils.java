package ru.finam.trade.common;

import lombok.experimental.UtilityClass;

import java.time.LocalTime;
import java.util.List;

@UtilityClass
public class ErrorUtils {
    private static final LocalTime START_TIME_MARKET = LocalTime.parse("07:00:00");
    private static final List<String> IGNORED_ERRORS = List.of("FIX server is offline", "Session is not available");

    public static boolean isNeedLoggingError(Throwable error) {
        return (error.getLocalizedMessage() == null || IGNORED_ERRORS.stream().noneMatch(e -> error.getLocalizedMessage().contains(e)))
                && isStartTradeMarket();
    }

    private static boolean isStartTradeMarket() {
        return DateUtils.now().toLocalTime().isAfter(START_TIME_MARKET);
    }
}
