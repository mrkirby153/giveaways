package com.mrkirby153.snowsgivingbot.entity.repo;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
import org.springframework.data.repository.CrudRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface GiveawayRepository extends CrudRepository<GiveawayEntity, Long> {

    Optional<GiveawayEntity> findByMessageId(String messageId);

    List<GiveawayEntity> findAllByEndsAtBeforeAndStateIs(Timestamp timestamp, GiveawayState state);

    List<GiveawayEntity> findAllByState(GiveawayState state);

    void deleteAllByMessageId(String messageId);

    List<GiveawayEntity> findAllByGuildId(String guild);
}
