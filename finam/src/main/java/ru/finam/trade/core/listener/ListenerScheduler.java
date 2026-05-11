package ru.finam.trade.core.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListenerScheduler {
    private final List<AbstractListener> abstractListeners;

    @Scheduled(cron = "0 0 7 * * *")
    public void startToListen() {
        abstractListeners.stream().sorted(Comparator.comparingInt(AbstractListener::getOrder)).forEach(AbstractListener::startToListen);
    }

    @Scheduled(cron = "59 59 23 * * *")
    public void stopToListen() {
        abstractListeners.forEach(AbstractListener::stopToListen);
    }
}
