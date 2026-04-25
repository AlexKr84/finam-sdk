package ru.finam.trade.common;

import com.google.protobuf.Timestamp;
import com.google.type.Date;
import lombok.experimental.UtilityClass;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@UtilityClass
public class DateUtils {

    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Europe/Moscow");
    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String SHORT_PATTERN = "yyyy-MM-dd";
    private static final ZoneOffset DEFAULT_ZONE_OFFSET = DEFAULT_ZONE_ID.getRules().getOffset(LocalDateTime.now());

    /**
     * Преобразование java {@link Long} в google {@link Timestamp}.
     *
     * @param epochSeconds Экземпляр {@link Long}.
     * @return Эквивалентный {@link Timestamp}.
     */
    public static Timestamp epochToTimestamp(Long epochSeconds) {
        return Timestamp.newBuilder().setSeconds(epochSeconds).build();
    }

    /**
     * Преобразование java {@link OffsetDateTime} в google {@link Timestamp}.
     *
     * @param offsetDateTime Экземпляр {@link OffsetDateTime}.
     * @return Эквивалентный {@link Timestamp}.
     */
    public static Timestamp offsetDateTimeToTimestamp(OffsetDateTime offsetDateTime) {
        Instant instant = offsetDateTime.toInstant();

        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    /**
     * Преобразование java {@link Timestamp} в java {@link LocalDate}.
     *
     * @param timestamp Экземпляр google {@link Timestamp}.
     * @return Эквивалентный {@link LocalDate}.
     */
    public static LocalDate epochSecondToLocalDate(Timestamp timestamp) {
        return Instant.ofEpochMilli(timestamp.getSeconds() * 1000).atZone(DEFAULT_ZONE_OFFSET).toLocalDate();
    }

    /**
     * Преобразование java {@link OffsetDateTime} в java {@link Long}.
     * Количество секунд в формате epoch будет по часовому поясу UTC
     *
     * @param offsetDateTime Экземпляр {@link OffsetDateTime}.
     * @return Эквивалентный {@link Long}.
     */
    public static Long offsetDateTimeToLong(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }

        return offsetDateTime.toInstant().getEpochSecond();
    }

    /**
     * Преобразование java {@link Instant} в google {@link Timestamp}.
     *
     * @param i Экземпляр {@link Instant}.
     * @return Эквивалентный {@link Timestamp}.
     */
    public static Timestamp instantToTimestamp(Instant i) {
        return Timestamp.newBuilder()
                .setSeconds(i.getEpochSecond())
                .setNanos(i.getNano())
                .build();
    }

    /**
     * Преобразование java {@link ZonedDateTime} в google {@link Timestamp}.
     *
     * @param zonedDateTime Экземпляр {@link ZonedDateTime}.
     * @return Эквивалентный {@link Timestamp}.
     */
    public static Timestamp zonedDateTimeToTimestamp(ZonedDateTime zonedDateTime) {
        Instant instant = zonedDateTime.toInstant();

        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    /**
     * Преобразование google {@link Timestamp} в java {@link Instant}.
     *
     * @param t Экземпляр {@link Timestamp}.
     * @return Эквивалентный {@link Instant}.
     */
    public static Instant timestampToInstant(Timestamp t) {
        return Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
    }

    /**
     * Преобразование google {@link Timestamp} в java {@link ZonedDateTime}.
     *
     * @param t Экземпляр {@link Timestamp}.
     * @return Эквивалентный {@link ZonedDateTime}.
     */
    public static ZonedDateTime timestampToZonedDateTime(Timestamp t) {
        return timestampToInstant(t).atZone(DEFAULT_ZONE_ID);
    }

    /**
     * Преобразование google {@link Timestamp} в java {@link ZonedDateTime} с отсечением времени.
     *
     * @param t Экземпляр {@link Timestamp}.
     * @return Эквивалентный {@link ZonedDateTime}.
     */
    public static ZonedDateTime timestampToZonedDate(Timestamp t) {
        return timestampToZonedDateTime(t).truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Возвращает текстовое представление даты в виде 2021-09-27T11:05:27Z (GMT)
     *
     * @param timestamp Время в формате Timestamp (google)
     * @return текстовое представление даты в виде 2021-09-27T11:05:27Z
     */
    public static String timestampToString(Timestamp timestamp) {
        var zonedDateTime = timestampToZonedDateTime(timestamp);
        return zonedDateTime.format(DateTimeFormatter.ofPattern(PATTERN));
    }

    /**
     * Возвращает текстовое представление даты в виде 2021-09-27T11:05:27Z (GMT)
     *
     * @param i Время в формате Instant
     * @return текстовое представление даты в виде 2021-09-27T11:05:27Z
     */
    public static String instantToString(Instant i) {
        var zonedDateTime = i.atZone(DEFAULT_ZONE_ID);
        return zonedDateTimeToString(zonedDateTime);
    }

    /**
     * Возвращает текстовое представление даты в виде 2021-09-27T11:05:27Z (GMT)
     *
     * @param z Время в формате ZonedDateTime
     * @return текстовое представление даты в виде 2021-09-27T11:05:27Z
     */
    public static String zonedDateTimeToString(ZonedDateTime z) {
        return z.format(DateTimeFormatter.ofPattern(PATTERN));
    }

    /**
     * Возвращает текущую дату {@link ZonedDateTime}
     *
     * @return текущая дата {@link ZonedDateTime}
     */
    public static ZonedDateTime now() {
        return ZonedDateTime.now(DEFAULT_ZONE_ID);
    }

    /**
     * Возвращает текущую дату {@link LocalDate}
     *
     * @return текущая дата {@link LocalDate}
     */
    public static LocalDate nowDate() {
        return LocalDate.now(DEFAULT_ZONE_ID);
    }

    /**
     * Преобразование java {@link LocalDateTime} в java {@link Instant}.
     *
     * @param l Экземпляр google {@link LocalDateTime}.
     * @return Эквивалентный {@link Instant}.
     */
    public static Instant localDateTimeToInstant(LocalDateTime l) {
        return l.toInstant(DEFAULT_ZONE_OFFSET);
    }

    /**
     * Преобразование java {@link LocalDate} в java {@link String}.
     *
     * @param l Экземпляр google {@link LocalDate}.
     * @return Эквивалентный {@link String}.
     */
    public static String localDateToString(LocalDate l) {
        return l.format(DateTimeFormatter.ofPattern(SHORT_PATTERN));
    }

    /**
     * Преобразование google {@link Date} в java {@link ZonedDateTime}.
     *
     * @param date Экземпляр {@link Date}.
     * @return Эквивалентный {@link ZonedDateTime}.
     */
    public static ZonedDateTime dateToZonedDateTime(Date date) {
        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay()).atStartOfDay(DEFAULT_ZONE_ID);
    }

    /**
     * Преобразование google {@link LocalDate} в java {@link ZonedDateTime}.
     *
     * @param date Экземпляр {@link LocalDate}.
     * @return Эквивалентный {@link ZonedDateTime}.
     */
    public static ZonedDateTime localDateToZonedDateTime(LocalDate date) {
        return date.atStartOfDay(DEFAULT_ZONE_ID);
    }
}
