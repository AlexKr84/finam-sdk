package ru.finam.trade.hedging.last_price;

import grpc.tradeapi.v1.marketdata.Quote;
import lombok.NoArgsConstructor;
import lombok.val;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.finam.trade.common.DateUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@NoArgsConstructor
public class LastPriceStorage implements LastPriceObserver{
    private static final String CSV_FILE_NAME_PATTERN = "last_price_%s.csv";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd_MM_yyyy");

    private final List<Quote> buffer = new ArrayList<>();

    @Override
    public void onChange(Quote quote) {
        buffer.add(quote);
        if (buffer.size() >= 1000) {
            saveBuffer();
        }
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void clearLastPrices() {
        saveBuffer();
    }

    private void saveBuffer() {
        buffer.stream()
                .collect(Collectors.groupingBy(lastPrice ->
                        DateUtils.timestampToZonedDateTime(lastPrice.getTimestamp()).toLocalDate()))
                .forEach((date, lastPrices) -> {
                    val dateStr = date.format(FORMATTER);
                    val file = new File(CSV_FILE_NAME_PATTERN.formatted(dateStr));
                    try (PrintWriter printWriter = new PrintWriter(new FileOutputStream(file, true))) {
                        lastPrices.stream()
                                .map(this::convertToCSV)
                                .forEach(printWriter::println);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
        buffer.clear();
    }

    private String convertToCSV(Quote quote) {
        return Stream.of(quote.getSymbol(),
                        quote.getLast().getValue(),
                        DateUtils.timestampToZonedDateTime(quote.getTimestamp()))
                .map(Object::toString)
                .map(this::escapeSpecialCharacters)
                .collect(Collectors.joining(","));
    }

    private String escapeSpecialCharacters(String data) {
        if (data == null) {
            throw new IllegalArgumentException("Input data cannot be null");
        }
        String escapedData = data.replaceAll("\\R", " ");
        if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("'")) {
            escapedData = escapedData.replace("\"", "\"\"");
            escapedData = "\"" + escapedData + "\"";
        }
        return escapedData;
    }
}
