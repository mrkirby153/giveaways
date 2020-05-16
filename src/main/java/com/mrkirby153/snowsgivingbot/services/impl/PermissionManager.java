package com.mrkirby153.snowsgivingbot.services.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mrkirby153.snowsgivingbot.entity.GiveawayRoleEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRoleRepository;
import com.mrkirby153.snowsgivingbot.services.PermissionService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PermissionManager implements PermissionService {

    private final JDA jda;
    private final GiveawayRoleRepository repo;

    private LoadingCache<String, List<GiveawayRoleEntity>> roleCache = CacheBuilder.newBuilder()
        .maximumSize(1000).expireAfterWrite(10, TimeUnit.MINUTES).build(
            new CacheLoader<String, List<GiveawayRoleEntity>>() {
                @Override
                public List<GiveawayRoleEntity> load(@NotNull String key) throws Exception {
                    return repo.findAllByGuildId(key);
                }
            });

    public PermissionManager(JDA jda, GiveawayRoleRepository repo) {
        this.jda = jda;
        this.repo = repo;
    }

    @Override
    public boolean hasPermission(Member member) {
        if (member.hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        } else {
            List<GiveawayRoleEntity> giveawayRoles;
            try {
                giveawayRoles = roleCache.get(member.getGuild().getId());
            } catch (ExecutionException e) {
                log.error("An error occurred getting the roles from the cache", e);
                return false;
            }
            List<String> memberRoles = member.getRoles().stream().map(Role::getId).collect(
                Collectors.toList());
            for (GiveawayRoleEntity gre : giveawayRoles) {
                if (memberRoles.contains(gre.getRoleId())) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void addGiveawayRole(Role role) {
        roleCache.invalidate(role.getGuild().getId());
        GiveawayRoleEntity gre = new GiveawayRoleEntity();
        gre.setGuildId(role.getGuild().getId());
        gre.setRoleId(role.getId());
        repo.save(gre);
    }

    @Override
    @Transactional
    public void removeGiveawayRole(Role role) {
        roleCache.invalidate(role.getGuild().getId());
        repo.removeByRoleId(role.getId());
    }

    @Override
    @Transactional
    public void refreshGiveawayRoles() {
        jda.getGuilds().forEach(guild -> {
            log.debug("Refreshing giveaway roles on {}", guild);
            List<String> roles = guild.getRoles().stream().map(Role::getId).collect(
                Collectors.toList());
            repo.removeAllByRoleIdNotInAndGuildId(roles, guild.getId());
        });
    }

    @Override
    public List<GiveawayRoleEntity> getGiveawayRoles(Guild guild) {
        try {
            return roleCache.get(guild.getId());
        } catch (ExecutionException e) {
            log.error("Could not get a list of giveaway roles", e);
        }
        return new ArrayList<>();
    }

    @EventListener
    @Transactional
    public void onRoleDelete(RoleDeleteEvent event) {
        removeGiveawayRole(event.getRole());
    }

    @EventListener
    @Transactional
    public void onReady(ReadyEvent event) {
        refreshGiveawayRoles();
    }
}
