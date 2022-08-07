package com.mrkirby153.giveaways.events

import com.mrkirby153.giveaways.jpa.GiveawayEntity

/**
 * Event fired when a giveaway starts
 */
data class GiveawayStartedEvent(val giveaway: GiveawayEntity)

/**
 * Event fired when a giveaway transitions to ending
 */
data class GiveawayEndingEvent(val giveaway: GiveawayEntity)