package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.Vote;
import com.mrkirby153.tgabot.entity.repo.OptionRoleRepository;
import com.mrkirby153.tgabot.entity.repo.VoteRepository;
import com.mrkirby153.tgabot.events.VoteCastEvent;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class OptionRoleManager implements OptionRoleService {

    private final OptionRoleRepository orr;
    private final VoteRepository vr;
    private final JDA jda;
    private final String tgaGuild;

    private HashMap<Option, String> optionRoleCache = new HashMap<>();
    private HashMap<String, Option> optionRoleRevCache = new HashMap<>();

    public OptionRoleManager(JDA jda, OptionRoleRepository orr, VoteRepository vr,
        @Value("${bot.guild}") String guild) {
        this.orr = orr;
        this.vr = vr;
        this.jda = jda;
        this.tgaGuild = guild;

        refreshOptionRoleCache();
    }

    @Override
    public void onVoteCast(VoteCastEvent event) {
        Guild tgaGuild = jda.getGuildById(this.tgaGuild);
        if (tgaGuild == null) {
            return;
        }
        User user = event.getUser();
        Vote vote = event.getVote();

        String roleId = optionRoleCache.get(vote.getOption());
        if (roleId == null) {
            return;
        }

        Role newRole = jda.getRoleById(roleId);
        if (newRole == null) {
            return;
        }
        Member member = tgaGuild.getMember(user);
        if (member == null) {
            return;
        }

        updateOptionRoleForUser(tgaGuild, vote, member);
    }

    @Override
    public void syncRoles() {
        Guild tgaGuild = jda.getGuildById(this.tgaGuild);
        if (tgaGuild == null) {
            return;
        }

        vr.getAllUserIds().stream().map(jda::getUserById).filter(Objects::nonNull).forEach(user -> {
            Member member = tgaGuild.getMember(user);
            if (member == null) {
                return;
            }
            vr.getAllByUser(user.getId())
                .forEach(vote -> updateOptionRoleForUser(tgaGuild, vote, member));
        });
    }

    @Override
    public void refreshOptionRoleCache() {
        log.debug("Refreshing option role cache");
        optionRoleRevCache.clear();
        optionRoleCache.clear();
        orr.findAll().forEach(role -> {
            optionRoleCache.put(role.getOption(), role.getRoleId());
            optionRoleRevCache.put(role.getRoleId(), role.getOption());
        });
    }

    /**
     * Updates a users roles to be in sync with their current vote
     *
     * @param tgaGuild The guild to operate in
     * @param vote     Their vote
     * @param member   The member
     */
    private void updateOptionRoleForUser(Guild tgaGuild, Vote vote, Member member) {
        Role newRole = jda.getRoleById(optionRoleCache.get(vote.getOption()));
        Map<Category, List<Role>> roleMap = new HashMap<>();
        member.getRoles().forEach(role -> {
            Option o = optionRoleRevCache.get(role.getId());
            if (o != null) {
                List<Role> r = roleMap.computeIfAbsent(o.getCategory(), v -> new ArrayList<>());
                r.add(role);
            }
        });
        List<Role> optionRoles = roleMap
            .getOrDefault(vote.getOption().getCategory(), Collections.emptyList());
        optionRoles.remove(newRole); // So we don't remove the role
        log.debug("Existing roles: {}", optionRoles);

        tgaGuild.modifyMemberRoles(member, Collections.singletonList(newRole), optionRoles).queue();
    }
}
