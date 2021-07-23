package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.services.AdminLoggerService;
import com.mrkirby153.snowsgivingbot.services.GiveawayMigrationService;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiveawayMigrationManager implements GiveawayMigrationService {

    /**
     * The version that new giveaways should be created at
     */
    public static final long LATEST_GIVEAWAY_VERSION = 2;

    private static final Map<Long, BiConsumer<GiveawayMigrationManager, GiveawayEntity>> migrations = new HashMap<>();

    static {
        // Update giveaway embed format
        migrations.put(2L, (manager, entity) -> {
            manager.giveawayService.renderGiveaway(entity);
        });
    }

    private final GiveawayRepository giveawayRepository;
    private final GiveawayService giveawayService;
    private final AdminLoggerService adminLoggerService;
    private final TaskScheduler taskScheduler;

    @Override
    public long getVersion(String messageId) {
        Optional<GiveawayEntity> e = giveawayRepository.findByMessageId(messageId);
        return e.map(GiveawayEntity::getVersion).orElse(-1L);
    }

    @Override
    public void doMigration(GiveawayEntity entity) {
        long start = System.currentTimeMillis();
        log.info("Migrating {} from v{} to v{}", entity.getId(), entity.getVersion(),
            LATEST_GIVEAWAY_VERSION);
        long currentVersion = entity.getVersion();
        while (currentVersion++ < LATEST_GIVEAWAY_VERSION) {
            log.debug("Running migration v{} for {}", currentVersion, entity.getId());
            BiConsumer<GiveawayMigrationManager, GiveawayEntity> migrationFunction = migrations
                .get(currentVersion);
            try {
                migrationFunction.accept(this, entity);
                entity.setVersion(currentVersion);
            } catch (Exception e) {
                log.error("Could not migrate {}", entity.getId(), e);
                adminLoggerService.log(
                    "Could not migrate giveaway " + entity.getId() + " to version "
                        + currentVersion + ": " + e.getMessage());
            }
        }
        giveawayRepository.save(entity);
        log.info("Migrated {} to v{} in {}", entity.getId(), entity.getVersion(),
            Time.format(1, System.currentTimeMillis() - start));
    }

    @Override
    public void migrateAll() {
        List<GiveawayEntity> giveaways = giveawayRepository.getAllGiveawaysNeedingMigration();
        log.info("Scheduling migration of {} giveaways", giveaways.size());
        if (giveaways.size() < 1) {
            return;
        }
        // Bucket our migrations to not ratelimit ourselves
        Map<Long, List<GiveawayEntity>> buckets = new HashMap<>();
        giveaways.forEach(g -> {
            long bucket = g.getId() % 300;
            List<GiveawayEntity> bucketContents = buckets
                .computeIfAbsent(bucket, b -> new ArrayList<>());
            bucketContents.add(g);
        });
        StringBuilder adminLogBuckets = new StringBuilder("Scheduling migration of ");
        adminLogBuckets.append(giveaways.size()).append(" giveaways: ");
        buckets
            .forEach((b, c) -> adminLogBuckets.append(b).append(":").append(c.size()).append(" "));
        log.info(adminLogBuckets.toString());
        buckets.forEach((bucket, bucketContents) -> taskScheduler
            .schedule(() -> bucketContents.forEach(this::doMigration),
                Instant.now().plusSeconds(bucket)));
        log.info("All giveaways have been scheduled");
    }
}
