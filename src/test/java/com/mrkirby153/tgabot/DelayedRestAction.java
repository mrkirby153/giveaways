package com.mrkirby153.tgabot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@RequiredArgsConstructor
@Slf4j
public class DelayedRestAction<T> implements RestAction<T> {

    private static final ExecutorService threadExecutorService = Executors.newFixedThreadPool(5);
    private static final List<DelayedRestAction<?>> runningRestActions = new CopyOnWriteArrayList<>();

    private final JDA jda;
    private final T obj;

    private final CountDownLatch latch = new CountDownLatch(1);
    private final Object stateLock = new Object();
    private Future<?> future;
    private boolean isCanceled = false;

    /**
     * Waits the specified time for all rest actions to complete
     *
     * @param duration The duration
     * @param timeUnit The time unit
     */
    public static void waitForRestActionTermination(long duration, TimeUnit timeUnit) {
        runningRestActions.removeIf(ra -> ra.future.isDone());
        log.info("Waiting for {} rest actions to complete", runningRestActions.size());
        AtomicLong timeLeft = new AtomicLong(TimeUnit.MILLISECONDS.convert(duration, timeUnit));
        runningRestActions.forEach(ra -> {
            long startTime = System.currentTimeMillis();
            try {
                if (!ra.future.isCancelled()) {
                    log.debug("Waiting up to {} milliseconds", timeLeft.get());
                    ra.future.get(timeLeft.get(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException | ExecutionException ignored) {
            } catch (TimeoutException e) {
                log.debug("Timed out. Killing");
                ra.abort();
            } finally {
                long endTime = System.currentTimeMillis();
                log.debug("Took {} to finish termination of rest action", endTime - startTime);
                long newTimeLeft = timeLeft.addAndGet(-(endTime - startTime));
                log.debug("There is {} time left to wait", newTimeLeft);
                if (newTimeLeft < 0) {
                    timeLeft.set(0);
                }
            }
        });
    }

    /**
     * Cancels all running rest actions
     */
    public static void cancelAllRunningRestActions() {
        runningRestActions.forEach(DelayedRestAction::abort);
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return jda;
    }

    @Nonnull
    @Override
    public RestAction<T> setCheck(@Nullable BooleanSupplier checks) {
        return this;
    }

    @Override
    public void queue(@Nullable Consumer<? super T> success,
        @Nullable Consumer<? super Throwable> failure) {
        future = threadExecutorService.submit(() -> {
            if (success != null) {
                waitForTrigger();
                assertNotCanceled();
                success.accept(obj);
            }
            runningRestActions.remove(this);
        });
        runningRestActions.add(this);
    }

    @Override
    public T complete(boolean shouldQueue) throws RateLimitedException {
        waitForTrigger();
        return obj;
    }

    @Nonnull
    @Override
    public CompletableFuture<T> submit(boolean shouldQueue) {
        CompletableFuture<T> future = new CompletableFuture<>();
        this.future = threadExecutorService.submit(() -> {
            waitForTrigger();
            assertNotCanceled();
            future.complete(obj);
            runningRestActions.remove(this);
        });
        runningRestActions.add(this);
        return future;
    }

    public void trigger() {
        assertNotCanceled();
        if (latch.getCount() == 0) {
            throw new IllegalStateException(
                "Attempted to trigger a delayed rest action that has already been triggered");
        }
        latch.countDown();
    }

    public void abort() {
        synchronized (stateLock) {
            isCanceled = true;
        }
        log.debug("Aborting DelayedRestAction");
        future.cancel(true);
    }

    private void waitForTrigger() {
        try {
            log.debug("Waiting for trigger");
            latch.await();
        } catch (InterruptedException ignored) {

        }
    }

    private void assertNotCanceled() {
        synchronized (stateLock) {
            if (isCanceled) {
                throw new IllegalStateException("Rest action has been canceled");
            }
        }
    }
}
