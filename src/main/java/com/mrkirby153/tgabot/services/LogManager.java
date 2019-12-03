package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.entity.ActionLog;
import com.mrkirby153.tgabot.entity.ActionLog.ActionType;
import com.mrkirby153.tgabot.entity.repo.ActionRepository;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.LinkedList;

@Service
@Slf4j
public class LogManager implements LogService, DisposableBean {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private final ActionRepository actionRepository;
    private final JDA jda;
    private final String logChannelId;

    private final LinkedList<String> pendingMessages = new LinkedList<>();
    private long quietPeriod = -1;

    private Thread logPumpThread;

    public LogManager(ActionRepository actionRepository, JDA jda,
        @Value("${bot.log-channel}") String logChannelId) {
        this.actionRepository = actionRepository;
        this.jda = jda;
        this.logChannelId = logChannelId;
    }

    /**
     * Gets the log name of a user
     *
     * @param user The user
     *
     * @return The log name
     */
    public static String getLogName(User user) {
        return MarkdownSanitizer.escape(user.getName()) + " (`" + user.getId() + "`)";
    }

    @Override
    public ActionLog recordAction(User user, ActionType actionType, String message) {
        return recordAction(user.getId(), actionType, message);
    }

    @Override
    public ActionLog recordAction(String user, ActionType actionType, String message) {
        log.debug("Recording action {} for user {}: {}", actionType, user, message);
        ActionLog log = new ActionLog(user, actionType);
        log.setData(message);
        return actionRepository.save(log);
    }

    @Override
    public Page<ActionLog> getActions(String user, Pageable pageable) {
        return actionRepository.getAllByUser(user, pageable);
    }

    @Override
    public void logMessage(String message) {
        String m = "`[" + TIME_FORMAT.format(System.currentTimeMillis()) + "]` " + message;
        if (m.length() >= 1990) {
            throw new IllegalArgumentException("Provided message was too long");
        }
        pendingMessages.add(m);
    }

    @EventListener
    public void onReady(ReadyEvent event) {
        logPumpThread = new Thread(new LogManagerRunnable());
        logPumpThread.setName("Log Pump");
        logPumpThread.setDaemon(true);
        logPumpThread.start();
    }

    private TextChannel getLogChannel() {
        return jda.getTextChannelById(this.logChannelId);
    }

    @Override
    public void destroy() throws Exception {
        log.info("Destroying LogManager");
        if (logPumpThread.isInterrupted()) {
            log.warn("Log pump thread has already been interrupted");
        }
        if (logPumpThread.isAlive()) {
            logPumpThread.interrupt();
        } else {
            log.warn("Log pump is already dead");
        }
    }

    private class LogManagerRunnable implements Runnable {

        @Override
        public void run() {
            log.info("Starting log pump");
            while (!Thread.interrupted()) {
                try {
                    if (!pendingMessages.isEmpty()) {
                        StringBuilder msg = new StringBuilder();
                        while (!pendingMessages.isEmpty() && msg.length() < 1990) {
                            String head = pendingMessages.peek() + '\n';
                            if (head.length() + msg.length() > 1990) {
                                break;
                            }
                            msg.append(head);
                            pendingMessages.pop();
                        }
                        if (msg.toString().isEmpty()) {
                            continue;
                        }
                        log.debug("Logging message {}", msg);
                        try {
                            getLogChannel().sendMessage(msg.toString()).complete(false);
                        } catch (RateLimitedException e) {
                            log.debug("Got ratelimited, entering quiet period");
                            quietPeriod = System.currentTimeMillis() + 60000;
                        }
                    }
                } catch (Exception e) {
                    log.error("An exception occurred in the log pump", e);
                }
                try {
                    if (quietPeriod != -1) {
                        if (quietPeriod < System.currentTimeMillis()) {
                            log.debug("Quiet period has expired");
                            quietPeriod = -1;
                        }
                        Thread.sleep(5000);
                    } else {
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    log.warn("Log pump was interrupted");
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Log pump exited");
        }
    }
}
