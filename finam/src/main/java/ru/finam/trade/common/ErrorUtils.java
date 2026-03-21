package ru.finam.trade.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorUtils {
    public static boolean isNeedLoggingError(Throwable error) {
        return error.getLocalizedMessage() == null || !error.getLocalizedMessage().contains("FIX server is offline");
    }
}
