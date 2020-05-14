package com.mrkirby153.snowsgivingbot.commands;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.services.RedisCacheService;
import com.mrkirby153.snowsgivingbot.services.backfill.BackfillTask;
import com.mrkirby153.snowsgivingbot.services.backfill.GiveawayBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommands {

    private final GiveawayBackfillService backfillService;
    private final GiveawayRepository giveawayRepository;
    private final RedisCacheService redisCacheService;
    private final RedisTemplate<String, String> template;

    @Command(name = "ping", clearance = 100)
    public void ping(Context context, CommandContext cmdContext) {
        long start = System.currentTimeMillis();
        context.getChannel().sendTyping().queue(v -> {
            context.getChannel()
                .sendMessage("Pong! " + Time.INSTANCE.format(1, System.currentTimeMillis() - start))
                .queue();
        });
    }

    @Command(name = "log-level", arguments = {"<logger:string>", "[level:string]"}, clearance = 101)
    public void logLevel(Context context, CommandContext cmdContext) {
        String loggerName = cmdContext.get("logger");
        String level = cmdContext.get("level");

        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);

        if (level != null) {
            try {
                Level prev = logger.getEffectiveLevel();
                Level lvl = Level.valueOf(level.toUpperCase());
                logger.setLevel(lvl);
                log.info("Log level changed from {} -> {} for [{}]", prev, lvl, loggerName);
            } catch (IllegalArgumentException e) {
                throw new CommandException("Log level not recognized");
            }

            context.getChannel().sendMessage(
                "Logger `" + loggerName + "` has been set to level `" + level + "`").queue();
        } else {
            context.getChannel().sendMessage(
                "Logger `" + loggerName + "` is at level `" + logger.getEffectiveLevel().toString()
                    + "`").queue();
        }
    }

    @Command(name = "backfill", arguments = {"<id:int>"}, clearance = 101)
    public void startBackfill(Context context, CommandContext cmdContext) {
        GiveawayEntity entity = giveawayRepository
            .findById(cmdContext.<Integer>getNotNull("id").longValue())
            .orElseThrow(() -> new CommandException("Giveaway not found!"));
        if (backfillService.getRunningGiveawayIDs().contains(entity.getId())) {
            throw new CommandException("Backfill is already in progress!");
        }
        BackfillTask task = backfillService.startBackfill(entity);
        task.getFuture().handle((count, throwable) -> {
            if (throwable != null) {
                context.getChannel().sendMessage(
                    "Backfill did not complete successfully for " + entity.getName() + ": "
                        + throwable.getMessage()).queue();
            } else {
                context.getChannel()
                    .sendMessage(
                        "Backfill of " + entity.getName() + " succeeded in " + Time.INSTANCE
                            .format(1, task.getTimeTaken()) + ": (" + count + ")")
                    .queue();
            }
            return null;
        });
        context.getChannel().sendMessage(
            "Starting backfill of " + entity.getName() + " (" + task.getId()
                + "). This may take a _long_ time").queue();
    }

    @Command(name = "status", parent = "backfill", clearance = 101)
    public void status(Context context, CommandContext cmdContext) {
        StringBuilder sb = new StringBuilder();
        backfillService.getRunning().forEach(task -> sb
            .append(String.format(" - %d: %d (%d processed)\n", task.getId(), task.getGiveawayId(),
                task.getProcessed())));
        if (sb.length() == 0) {
            sb.append("No tasks running");
        }
        context.getChannel().sendMessage(sb.toString()).queue();
    }

    @Command(name = "cancel", parent = "backfill", clearance = 101, arguments = {"<id:int>"})
    public void cancel(Context context, CommandContext cmdContext) {
        BackfillTask task = backfillService.getRunning().stream()
            .filter(t -> t.getId() == cmdContext.<Integer>getNotNull("id")).findFirst()
            .orElseThrow(() -> new CommandException("Backfill task not running"));
        task.cancel();
        context.getChannel().sendMessage("Canceled task!").queue();
    }

    @Command(name = "cache", clearance = 101)
    public void cacheStats(Context context, CommandContext cmdContext) {
        StringBuilder sb = new StringBuilder();
        redisCacheService.allQueues().forEach((queue, size) -> sb.append(" - ").append(queue)
            .append(": ").append(size).append("\n"));
        if (sb.length() == 0) {
            sb.append("All queues are empty!");
        }
        context.getChannel().sendMessage(sb.toString()).queue();
    }

    @Command(name = "workers", clearance = 101, arguments = {"<sleep:int>", "<batch:int>"})
    public void workerSettings(Context context, CommandContext cmdContext) {
        int batch = cmdContext.getNotNull("batch");
        int sleep = cmdContext.getNotNull("sleep");
        redisCacheService.updateWorkerSettings(batch, sleep);
        context.getChannel().sendMessage("Updated!").queue();
    }

    @Command(name = "heartbeat", clearance = 101)
    public void getHeartbeat(Context context, CommandContext commandContext) {
        String lastheartbeat = template.opsForValue().get("heartbeat");
        if (lastheartbeat == null) {
            context.getChannel().sendMessage("! Last heartbeat was >30s ago !").queue();
            return;
        }
        long last = Long.parseLong(lastheartbeat);
        context.getChannel().sendMessage("Last heartbeat from worker was " + Time.INSTANCE
            .format(1, System.currentTimeMillis() - last) + " ago").queue();
    }
}
