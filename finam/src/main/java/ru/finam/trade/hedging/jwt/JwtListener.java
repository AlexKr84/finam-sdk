package ru.finam.trade.hedging.jwt;

import grpc.tradeapi.v1.auth.SubscribeJwtRenewalResponse;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.ErrorUtils;
import ru.finam.trade.core.stream.StreamProcessor;
import ru.finam.trade.notification.NotificationService;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "hedging.enabled", havingValue = "true")
@Slf4j
public class JwtListener {
    private final FinamApi api;
    private final NotificationService notificationService;
    private final StreamProcessor<SubscribeJwtRenewalResponse> processor;
    private final ScheduledExecutorService executorService;

    @SneakyThrows
    public JwtListener(NotificationService notificationService,
                       FinamApi api,
                       List<? extends TokenObserver> jwtObservers
    ) {
        this.api = api;
        this.notificationService = notificationService;
        executorService = Executors.newScheduledThreadPool(1);

        processor = response -> {
            if (jwtObservers != null) {
                jwtObservers.forEach(observer -> observer.onToken(response.getToken()));
            }
        };
    }

    @PostConstruct
    public void startToListen() {
        if (api.getAuthStreamService().existSubscription()) {
            return;
        }

        api.getAuthStreamService().subscribeJwtRenewal(processor, this::onError, api.getSecret());
    }

    public void stopToListen() {
        api.getAuthStreamService().cancelSubscription();
    }

    public void onError(Throwable error) {
        if (ErrorUtils.isNeedLoggingError(error)) {
            log.error("jwt error", error);
            notificationService.sendMessage(error.toString());
        }
        stopToListen();

        executorService.schedule(this::startToListen, 5, TimeUnit.SECONDS);
    }
}
