package ru.finam.trade.common;

import com.google.type.Decimal;
import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.util.Strings;

import java.math.BigDecimal;

@UtilityClass
public class DecimalUtils {
    public static BigDecimal toBigDecimal(Decimal value) {
        if (value.getValue().isEmpty()) {
            return null;
        }

        return new BigDecimal(value.getValue());
    }

    public static Decimal toDecimal(BigDecimal value) {
        return Decimal.newBuilder()
                .setValue(value == null ? Strings.EMPTY : value.toString())
                .build();
    }
}
