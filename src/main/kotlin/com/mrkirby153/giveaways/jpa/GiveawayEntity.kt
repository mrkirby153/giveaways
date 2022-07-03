package com.mrkirby153.giveaways.jpa

import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import java.sql.Timestamp
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EntityListeners
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.OneToMany
import javax.persistence.Table

enum class GiveawayState {
    RUNNING,
    ENDED,
    ENDING
}

/**
 * Entity representing a giveaway
 */
@Entity
@Table(name = "giveaways")
@EntityListeners(AuditingEntityListener::class)
class GiveawayEntity(
    var name: String,
    @Column(name = "guild_id")
    val guildId: String,
    @Column(name = "channel_id")
    val channelId: String,
    @Column(name = "message_id")
    var messageId: String,
    var winners: Int,
    var secret: Boolean = false,
    val createdAt: Timestamp,
    var endsAt: Timestamp,
    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "giveaway")
    var entrants: MutableList<GiveawayEntrantEntity>,
    @Enumerated(EnumType.ORDINAL)
    var state: GiveawayState = GiveawayState.RUNNING,
    private var finalWinners: String? = null,
    var version: Long = 2,
) : AutoIncrementingJpaEntity<Long>() {

    /**
     * Gets the winners of the giveaway
     */
    fun getWinners() = finalWinners?.split(",") ?: emptyList()

    /**
     * Sets the winners of the giveaway
     */
    fun setWinners(winners: Array<String>?) {
        finalWinners = winners?.joinToString(",")
    }
}

interface GiveawayRepository : JpaRepository<GiveawayEntity, Long> {
}