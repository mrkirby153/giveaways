package com.mrkirby153.snowsgivingbot.entity.repo;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface GiveawayRepository extends CrudRepository<GiveawayEntity, Long> {

    Optional<GiveawayEntity> findByMessageId(String messageId);

    List<GiveawayEntity> findAllByEndsAtBeforeAndStateIs(Timestamp timestamp, GiveawayState state);

    List<GiveawayEntity> findAllByState(GiveawayState state);

    List<GiveawayEntity> findAllByGuildIdAndState(String guildId, GiveawayState state);

    void deleteAllByMessageId(String messageId);

    List<GiveawayEntity> findAllByGuildId(String guild);

    @Query("SELECT e FROM GiveawayEntity e WHERE e.guildId = (:guild) AND e.channelId IN (:channels) ORDER BY e.endsAt ASC")
    List<GiveawayEntity> getAllGiveawaysInChannel(String guild, List<String> channels);
}
