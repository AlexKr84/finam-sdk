package ru.finam.trade.hedging.jwt;

import grpc.tradeapi.v1.auth.SubscribeJwtRenewalResponse;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.finam.trade.api.FinamApi;
import ru.finam.trade.common.AbstractListener;
import ru.finam.trade.core.stream.StreamProcessor;
import ru.finam.trade.notification.NotificationService;

import java.util.List;

@Service
@ConditionalOnProperty(name = "hedging.enabled", havingValue = "true")
@Slf4j
public class JwtListener extends AbstractListener {
    private final FinamApi api;
    private final StreamProcessor<SubscribeJwtRenewalResponse> processor;

    @SneakyThrows
    public JwtListener(NotificationService notificationService,
                       FinamApi api,
                       List<? extends TokenObserver> jwtObservers
    ) {
        super(notificationService);
        this.api = api;

        processor = response -> {
            if (jwtObservers != null) {
                jwtObservers.forEach(observer -> observer.onToken(response.getToken()));
            }
        };
    }

    @PostConstruct
    @Override
    public void startToListen() {
        if (api.getAuthStreamService().existSubscription()) {
            return;
        }

        api.getAuthStreamService().subscribeJwtRenewal(processor, this::onError, api.getSecret());
    }

    @Override
    public void stopToListen() {
        api.getAuthStreamService().cancelSubscription();
    }

    @Override
    public String getPrefixError() {
        return "jwt error";
    }
}
