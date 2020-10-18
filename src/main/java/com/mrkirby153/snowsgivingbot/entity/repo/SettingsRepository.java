package com.mrkirby153.snowsgivingbot.entity.repo;

import com.mrkirby153.snowsgivingbot.entity.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface SettingsRepository extends JpaRepository<SettingEntity, Long> {

    Optional<SettingEntity> getByGuildAndKey(String guildId, String key);

    List<SettingEntity> getAllByGuild(String guildId);

    void deleteByGuildAndKey(String guildId, String key);
}
