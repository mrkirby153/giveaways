package com.mrkirby153.snowsgivingbot.entity.repo;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface GiveawayRepository extends CrudRepository<GiveawayEntity, Long> {

    Optional<GiveawayEntity> findByMessageId(String messageId);

    List<GiveawayEntity> findAllByEndsAtBeforeAndStateIs(Timestamp timestamp, GiveawayState state);

    List<GiveawayEntity> findAllByState(GiveawayState state);

    long countAllByState(GiveawayState state);

    List<GiveawayEntity> findAllByGuildIdAndState(String guildId, GiveawayState state);

    void deleteAllByMessageId(String messageId);

    List<GiveawayEntity> findAllByGuildId(String guild);

    @Query("SELECT e FROM GiveawayEntity e WHERE e.guildId = (:guild) AND e.channelId IN (:channels) AND e.state = com.mrkirby153.snowsgivingbot.entity.GiveawayState.RUNNING ORDER BY e.endsAt ASC")
    List<GiveawayEntity> getAllActiveGiveawaysInChannel(String guild, List<String> channels);

    @Query("SELECT e FROM GiveawayEntity e WHERE e.guildId = (:guild) AND e.channelId IN (:channels) AND e.state = com.mrkirby153.snowsgivingbot.entity.GiveawayState.ENDED AND e.endsAt > (:after) ORDER BY e.endsAt DESC")
    List<GiveawayEntity> getExpiredGiveaways(String guild, List<String> channels, Timestamp after);

    void getAllByVersion(long version);

    void getAllByVersionAndState(long version, GiveawayState state);

    @Query("SELECT e from GiveawayEntity e WHERE e.version < com.mrkirby153.snowsgivingbot.services.impl.GiveawayMigrationManager.LATEST_GIVEAWAY_VERSION")
    List<GiveawayEntity> getAllGiveawaysNeedingMigration();
}
