package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayRoleEntity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;

public interface PermissionService {

    /**
     * Checks if the member has permission to interact with the bot
     *
     * @param member The member
     *
     * @return True if the user has permission
     */
    boolean hasPermission(Member member);

    /**
     * Adds a role as a giveaway role
     *
     * @param role The role to add as a giveaway role
     */
    void addGiveawayRole(Role role);

    /**
     * Removes a role as a giveaway role
     *
     * @param role The role to remove
     */
    void removeGiveawayRole(Role role);


    /**
     * Ensure old deleted roles are no longer in the database
     */
    void refreshGiveawayRoles();

    /**
     * Gets a list of all the giveaway roles configured
     *
     * @param guild The guild
     *
     * @return The role
     */
    List<GiveawayRoleEntity> getGiveawayRoles(Guild guild);
}
