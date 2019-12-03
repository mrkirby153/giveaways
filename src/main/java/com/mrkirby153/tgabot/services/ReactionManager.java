package com.mrkirby153.tgabot.services;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReactionManager implements ReactionService {

    private final ThreadPoolExecutor executor;

    private final ConcurrentHashMap<QueuedReactionTask, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public ReactionManager() {
        this.executor = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());
    }

    @Override
    public CompletableFuture<Result> removeReaction(Message message, User user, String reaction) {
        log.debug("Removing {} from {} by {}", reaction, message.getId(), user);
        Optional<MessageReaction> reactionOpt = message.getReactions().stream().filter(r -> {
            ReactionEmote re = r.getReactionEmote();
            return re.isEmoji() && re.getEmoji().equals(reaction);
        }).findFirst();
        return scheduleRemoval(message, user, reactionOpt);
    }

    @Override
    public CompletableFuture<Result> removeReaction(Message message, User user, Emote emote) {
        log.debug("Removing {} from {} by {}", emote.toString(), message.getId(), user);
        Optional<MessageReaction> reactionOpt = message.getReactions().stream().filter(r -> {
            ReactionEmote re = r.getReactionEmote();
            return re.isEmote() && re.getEmote() == emote;
        }).findFirst();
        return scheduleRemoval(message, user, reactionOpt);
    }

    @Override
    public CompletableFuture<Result> removeAllReactions(Message message) {
        log.debug("Removing all reactions from {}", message.getId());
        // Remove all pending
        List<Entry<QueuedReactionTask, Future<?>>> toCancelAndRemove = runningTasks.entrySet()
            .stream().filter(entry -> {
                QueuedReactionTask qrt = entry.getKey();
                return qrt.getMessageId().equals(message.getId())
                    && qrt.getType() == TaskType.SINGLE;
            }).collect(Collectors.toList());
        log.debug("There are {} tasks pending to cancel and remove", toCancelAndRemove.size());
        toCancelAndRemove.forEach(entry -> {
            log.debug("    Canceled task {}", entry.getKey().getTaskId());
            entry.getValue().cancel(true);
            entry.getKey().getFuture().complete(Result.ABORTED);
        });

        boolean existing = runningTasks.entrySet().stream().anyMatch(entry ->
            entry.getKey().getType() == TaskType.ALL
                && entry.getKey().getMessageId().equals(message.getId()));
        if (existing) {
            log.debug("There is already a task queued to remove all reactions, skipping");
            // No-op as there's already something queued
            return CompletableFuture.completedFuture(Result.NO_OP);
        }
        CompletableFuture<Result> cf = new CompletableFuture<>();
        QueuedReactionTask qrt = new QueuedReactionTask(message.clearReactions(), TaskType.ALL, cf,
            message.getId());

        submitQueuedReactionTask(qrt);
        return cf;
    }

    @Override
    public long pendingRemovals(Message message) {
        return runningTasks.entrySet().stream().filter(task -> !task.getValue().isDone())
            .filter(task -> task.getKey().getMessageId().equals(message.getId())).count();
    }

    @Override
    public void resizeThreadPool(int newSize) {
        log.info("Resizing thread pool from {} to {}", executor.getCorePoolSize(), newSize);
        executor.setCorePoolSize(newSize);
        executor.setMaximumPoolSize(newSize);
    }

    @Scheduled(fixedRate = 1000)
    public void clearFinishedTasks() {
        List<QueuedReactionTask> finished = runningTasks.keySet().stream()
            .filter(task -> task.getFuture().isDone()).collect(
                Collectors.toList());
        if (!finished.isEmpty()) {
            log.debug("Cleaning {} finished tasks", finished.size());
            finished.forEach(runningTasks::remove);
        }
    }

    @NotNull
    private CompletableFuture<Result> scheduleRemoval(Message message, User user,
        Optional<MessageReaction> reactionOpt) {
        if (!reactionOpt.isPresent()) {
            // No-op if there's no reaction present
            return CompletableFuture.completedFuture(Result.NO_OP);
        }
        CompletableFuture<Result> cf = new CompletableFuture<>();
        QueuedReactionTask task = new QueuedReactionTask(reactionOpt.get().removeReaction(user),
            TaskType.SINGLE, cf, message.getId());

        submitQueuedReactionTask(task);
        return cf;
    }

    private void submitQueuedReactionTask(QueuedReactionTask qrt) {
        log.debug("Submitting task to executor with id {}", qrt.getTaskId());
        Future<?> f = executor.submit(qrt.getRunnable());
        runningTasks.put(qrt, f);
    }


    enum TaskType {
        ALL,
        SINGLE
    }

    @Data
    protected static class QueuedReactionTask {

        private static long nextTaskId = 1;

        private final long taskId = nextTaskId++;
        private final RestAction<?> action;
        private final TaskType type;
        private final CompletableFuture<Result> future;
        private final String messageId;
        private final RestActionRunnable runnable = new RestActionRunnable(this);
    }

    @RequiredArgsConstructor
    protected static class RestActionRunnable implements Runnable {

        private final QueuedReactionTask task;

        @Override
        public void run() {
            try {
                log.debug("RAR for task {} started", task.getTaskId());
                task.getAction().complete();
                task.getFuture().complete(Result.SUCCESS);
                log.debug("RAR for task {} has finished", task.getTaskId());
            } catch (Exception e) {
                task.getFuture().completeExceptionally(e);
            }
        }
    }
}
