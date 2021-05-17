package com.mrkirby153.snowsgivingbot.entity.repo;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import lombok.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EntrantRepository extends CrudRepository<GiveawayEntrantEntity, Long> {

    List<GiveawayEntrantEntity> findAllByGiveaway(GiveawayEntity entity);

    @Query("SELECT DISTINCT e.userId from GiveawayEntrantEntity e WHERE e.giveaway.id = (:giveawayId) AND e.userId IN :users")
    List<String> findAllPreviouslyEntered(@Param("giveawayId") long giveawayId, @Param("users") Iterable<String> users);

    @Query("SELECT DISTINCT e.userId FROM GiveawayEntrantEntity e WHERE e.giveaway = (:entity)")
    List<String> findAllIdsFromGiveaway(@Param("entity") GiveawayEntity entity);

    boolean existsByGiveawayAndUserId(GiveawayEntity giveaway, String userId);

    @Async
    @Query("SELECT case when count(e) > 0 then true else false end from GiveawayEntrantEntity e WHERE e.giveaway = (:giveaway) AND e.userId = (:userId)")
    CompletableFuture<Boolean> asyncExistsByGiveawayAndUserId(@NonNull @Param("giveaway") GiveawayEntity giveaway,
        @NonNull @Param("userId") String userId);

    @Query("SELECT e FROM GiveawayEntrantEntity e WHERE e.userId = (:user) AND e.giveaway.guildId = (:guild)")
    List<GiveawayEntrantEntity> findAllByUserInGuild(@Param("user") String user, @Param("guild") String guild);
}
