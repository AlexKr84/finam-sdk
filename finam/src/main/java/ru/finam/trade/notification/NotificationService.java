package ru.finam.trade.notification;

import jakarta.ws.rs.core.UriBuilder;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import ru.finam.trade.config.TelegramConfiguration;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class NotificationService {
    private final HttpClient client;
    private final String chatId;
    private final String token;

    public NotificationService(HttpClient client,
                               TelegramConfiguration telegramConfiguration) {
        this.client = client;
        this.chatId = telegramConfiguration.getChatId();
        this.token = telegramConfiguration.getToken();
    }

    @SneakyThrows
    public void sendMessage(String message) {
        UriBuilder builder = UriBuilder
                .fromUri("https://api.telegram.org")
                .path("/{token}/sendMessage")
                .queryParam("chat_id", chatId)
                .queryParam("text", message);

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(builder.build("bot" + token))
                .timeout(Duration.ofSeconds(5))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
