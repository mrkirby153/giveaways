package com.mrkirby153.snowsgivingbot.entity.repo;

import com.mrkirby153.snowsgivingbot.entity.GiveawayRoleEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface GiveawayRoleRepository extends CrudRepository<GiveawayRoleEntity, Long> {

    Optional<GiveawayRoleRepository> findById(long id);

    Optional<GiveawayRoleEntity> findByRoleId(String id);

    List<GiveawayRoleEntity> findAllByGuildId(String guildId);

    void removeAllByRoleIdNotInAndGuildId(List<String> roles, String guildId);

    void removeByRoleId(String role);
}
