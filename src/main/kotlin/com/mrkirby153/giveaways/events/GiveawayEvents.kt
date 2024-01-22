package com.mrkirby153.giveaways.events

import com.mrkirby153.giveaways.jpa.GiveawayEntity

/**
 * Event fired when a giveaway starts
 */
data class GiveawayStartedEvent(val giveawayId: Long, val giveaway: GiveawayEntity? = null) {
    constructor(giveaway: GiveawayEntity) : this(giveaway.id!!, giveaway)
}

/**
 * Event fired when a giveaway ends. This is received as a RabbitMQ broadcast
 */
data class GiveawayEndedEvent(val giveawayId: Long, val giveaway: GiveawayEntity? = null) {
    constructor(giveaway: GiveawayEntity) : this(giveaway.id!!, giveaway)
}