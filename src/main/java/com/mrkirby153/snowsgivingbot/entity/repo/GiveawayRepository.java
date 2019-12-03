package com.mrkirby153.snowsgivingbot.entity.repo;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface GiveawayRepository extends CrudRepository<GiveawayEntity, Long> {

    Optional<GiveawayEntity> findByMessageId(String messageId);
}
