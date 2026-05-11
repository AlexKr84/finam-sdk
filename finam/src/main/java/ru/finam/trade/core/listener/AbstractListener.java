package ru.finam.trade.core.listener;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.finam.trade.common.ErrorUtils;
import ru.finam.trade.notification.NotificationService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public abstract class AbstractListener {
    protected final NotificationService notificationService;
    private final ScheduledExecutorService executorService;
    private final AtomicInteger errorDuplicateCounter;
    private volatile String lastErrorMessage;

    public AbstractListener(NotificationService notificationService) {
        this.notificationService = notificationService;
        executorService = Executors.newScheduledThreadPool(1);
        errorDuplicateCounter = new AtomicInteger(0);
    }

    public abstract void startToListen();

    public abstract void stopToListen();

    public abstract String getPrefixError();

    public abstract int getOrder();

    public void onError(Throwable error) {
        checkErrorOnDuplicate(error);
        if (ErrorUtils.isNeedLoggingError(error)) {
            log.error(getPrefixError(), error);
            notificationService.sendMessage(error instanceof StatusRuntimeException ?
                    ((StatusRuntimeException) error).getStatus() : error);
        }
        stopToListen();

        executorService.schedule(this::startToListen, calcDelay(), TimeUnit.SECONDS);
    }

    private void checkErrorOnDuplicate(Throwable error) {
        val errorMessage = error.getLocalizedMessage();
        if (lastErrorMessage != null && lastErrorMessage.equals(errorMessage)) {
            errorDuplicateCounter.incrementAndGet();
        } else {
            errorDuplicateCounter.set(0);
            lastErrorMessage = errorMessage;
        }
    }

    private long calcDelay() {
        long delay = 5 * (long) Math.pow(2, errorDuplicateCounter.get());
        if (delay > 300) {
            delay = 300;
        }
        return delay;
    }
}
