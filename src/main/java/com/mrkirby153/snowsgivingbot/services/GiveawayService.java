package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GiveawayService {


    /**
     * Creates a new giveaway
     *
     * @param channel The channel the giveaway is to be created in
     * @param name    The name of the giveaway
     * @param winners The number of winners
     * @param endsIn  The end string (i.e. "3m"
     * @param secret  If the giveaway is a secret giveaway
     * @param host    The user hosting the giveaway
     *
     * @return A completable future of the {@link GiveawayEntity} created
     */
    CompletableFuture<GiveawayEntity> createGiveaway(TextChannel channel, String name, int winners,
        String endsIn, boolean secret, User host);

    /**
     * Deletes a giveaway by its message id
     *
     * @param messageId The message id
     */
    void deleteGiveaway(String messageId);

    /**
     * Deletes a giveaway
     *
     * @param entity The giveaway entity to delete
     */
    void deleteGiveaway(GiveawayEntity entity);

    /**
     * Picks winners for the provided giveaway
     *
     * @param giveaway The giveaway to determine winners for
     *
     * @return The list of winners
     */
    List<String> determineWinners(GiveawayEntity giveaway);

    /**
     * Picks winners for the provided giveaway
     *
     * @param giveaway        The giveaway to determine winners for
     * @param existingWinners A list of winners to keep
     *
     * @return The list of winners
     */
    List<String> determineWinners(GiveawayEntity giveaway, List<String> existingWinners,
        int amount);

    /**
     * Immediately ends the giveaway with the provided message id
     *
     * @param messageId The message id of the giveaway
     */
    void endGiveaway(String messageId);

    /**
     * Rerolls a giveaway
     *
     * @param mid   The message id of the giveaway
     * @param users A list of winners to re-roll
     */
    void reroll(String mid, String[] users);

    /**
     * Gets all the giveaways in a guild
     *
     * @param guild The guild
     *
     * @return A list of giveaways
     */
    List<GiveawayEntity> getAllGiveaways(Guild guild);

    /**
     * Enters the given user into the provided giveaway
     *
     * @param user   The user to enter into the giveaway
     * @param entity The giveaway to enter
     */
    void enterGiveaway(User user, GiveawayEntity entity);

    /**
     * Gets the custom emote that the bot is using for giveaways
     *
     * @return The emote, or null if it is not using a custom emote
     *
     * @see GiveawayService#getGiveawayEmoji()
     */
    Emote getGiveawayEmote();

    /**
     * Gets the emoji that the bot is using for giveaways
     *
     * @return The emoji, orn ull if it is using a custom emote
     *
     * @see GiveawayService#getGiveawayEmote()
     */
    String getGiveawayEmoji();

    /**
     * Updates a giveaway
     *
     * @param entity The giveaway to update
     */
    void update(GiveawayEntity entity);

    /**
     * Renders a giveaway's embed
     *
     * @param entity The giveaway to render
     */
    void renderGiveaway(GiveawayEntity entity);

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    class ConfiguredGiveawayEmote {

        private boolean custom;
        private String emote;
    }
}
