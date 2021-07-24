package com.mrkirby153.snowsgivingbot.services.setting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrkirby153.snowsgivingbot.entity.SettingEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.SettingsRepository;
import com.mrkirby153.snowsgivingbot.services.setting.settings.ObjectSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingManager implements SettingService {

    /**
     * A list of setting keys that are considered "alpha"
     */
    private static final List<String> alphaSettings = new ArrayList<>();

    static {
        alphaSettings.add(Settings.USE_BUTTONS.getKey());
    }

    private final SettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;


    @PostConstruct
    public void postConstruct() {
        ObjectSetting.setObjectMapper(objectMapper);
    }

    @Override
    @CacheEvict(cacheNames = "settings", key = "#p0.getKey()+'-'+#p1.getId()")
    public void set(GuildSetting<?> setting, Guild guild, Object value) {
        set(setting, guild.getId(), value);
    }

    @Override
    @CacheEvict(cacheNames = "settings", key = "#p0.getKey()+'-'+#p1")
    public void set(GuildSetting<?> setting, String guildId, Object value) {
        final String key = setting.getKey();
        log.debug("Setting {} = {} on {}", key, value, guildId);
        Optional<SettingEntity> existing = settingsRepository
            .getByGuildAndKey(guildId, setting.getKey());
        existing.ifPresent(e -> {
            if ((setting.getDefaultSetting() != null && setting.getDefaultSetting().equals(value))
                || (setting.getDefaultSetting() == null && value == null)) {
                log.debug("Deleting {} on {} because it's being reset to default", key,
                    guildId);
                settingsRepository.delete(e);
            } else {
                e.setValue(setting.serialize(value));
                settingsRepository.save(e);
            }
        });
        if (!existing.isPresent()) {
            log.debug("Creating {} on {}", key, guildId);
            SettingEntity entity = new SettingEntity(guildId, key,
                setting.serialize(value));
            settingsRepository.save(entity);
        }
    }

    @Override
    public <T> T get(GuildSetting<T> setting, Guild guild) {
        return get(setting, guild.getId());
    }

    @Override
    public <T> T get(GuildSetting<T> setting, String guildId) {
        log.debug("Retrieving {} on {}", setting.getKey(), guildId);
        Optional<T> existing = settingsRepository.getByGuildAndKey(guildId, setting.getKey())
            .map(e -> setting.deserialize(e.getValue()));
        return existing.orElse(setting.getDefaultSetting());
    }

    @Override
    @CacheEvict(cacheNames = "settings", key = "#p0.getKey()+'-'+#p1.getId()")
    @Transactional
    public void reset(GuildSetting<?> setting, Guild guild) {
        reset(setting, guild.getId());
    }

    @Override
    @CacheEvict(cacheNames = "settings", key = "#p0.getKey()+'-'+#p1")
    @Transactional
    public void reset(GuildSetting<?> setting, String guildId) {
        log.debug("Resetting {} on {}", setting.getKey(), guildId);
        settingsRepository.deleteByGuildAndKey(guildId, setting.getKey());
    }

    @Override
    @Transactional
    public void optIntoAlpha(String alpha, Guild guild) {
        if (!alphaSettings.contains(alpha)) {
            throw new IllegalArgumentException("Provided setting was not an alpha setting");
        }
        List<String> existingAlphas = get(Settings.INCLUDED_ALPHAS, guild);
        if (existingAlphas == null) {
            // Guild is not in any alphas
            existingAlphas = new ArrayList<>();
        }
        existingAlphas.add(alpha);
        set(Settings.INCLUDED_ALPHAS, guild, existingAlphas);
    }

    @Override
    @Transactional
    public void removeFromAlpha(String alpha, Guild guild) {
        List<String> existingAlphas = get(Settings.INCLUDED_ALPHAS, guild);
        if (existingAlphas == null) {
            // Guild is not in any alphas, nothing to do
            return;
        }
        existingAlphas.remove(alpha);
        if (existingAlphas.isEmpty()) {
            reset(Settings.INCLUDED_ALPHAS, guild);
        } else {
            set(Settings.INCLUDED_ALPHAS, guild, existingAlphas);
        }
    }

    @Override
    public boolean inAlpha(String alpha, Guild guild) {
        List<String> existing = get(Settings.INCLUDED_ALPHAS, guild);
        if (existing == null) {
            return false;
        } else {
            return existing.contains(alpha);
        }
    }

    @Override
    public boolean isAlpha(GuildSetting<?> setting) {
        return alphaSettings.contains(setting.getKey());
    }
}
