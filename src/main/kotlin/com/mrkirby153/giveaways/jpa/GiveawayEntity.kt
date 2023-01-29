package com.mrkirby153.giveaways.jpa

import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.sql.Timestamp
import java.util.UUID
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
@LazyEntity
class GiveawayEntity(
    var name: String,
    @Column(name = "guild_id")
    var guildId: String,
    @Column(name = "channel")
    var channelId: String,
    @Column(name = "message")
    var messageId: String,
    var winners: Int,
    @Column(name = "host")
    var host: String?,
    @Column(name = "created_at")
    var createdAt: Timestamp? = null,
    @Column(name = "ends_at")
    var endsAt: Timestamp,
    @Column(name = "interaction_uuid")
    var interactionUuid: String = UUID.randomUUID().toString(),
    @Enumerated(EnumType.ORDINAL)
    var state: GiveawayState = GiveawayState.RUNNING,
    @Column(name = "final_winners")
    private var finalWinners: String? = null,
    var version: Long = 2,
) : AutoIncrementingJpaEntity<Long>() {

    @OneToMany(mappedBy = "giveaway", cascade = [CascadeType.ALL])
    lateinit var entrants: MutableList<GiveawayEntrantEntity>

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

    override fun toString(): String {
        return "GiveawayEntity(id='$id', name='$name', guildId='$guildId', channelId='$channelId')"
    }


}

interface GiveawayRepository : JpaRepository<GiveawayEntity, Long> {

    fun getFirstByMessageId(id: String): GiveawayEntity?

    @Query("SELECT e FROM GiveawayEntity e WHERE e.id = cast((:id) as long) OR e.messageId = (:id)")
    fun getFirstByMessageIdOrSnowflake(id: String): GiveawayEntity?

    @Query("SELECT e.interactionUuid from GiveawayEntity e WHERE e.state = com.mrkirby153.giveaways.jpa.GiveawayState.RUNNING")
    fun getAllInteractionUuidsForRunningGiveaways(): List<String>

    fun getAllByGuildIdInAndStateIn(
        guilds: List<String>,
        states: List<GiveawayState>
    ): List<GiveawayEntity>

    @Query("SELECT e FROM GiveawayEntity e WHERE e.guildId IN (:guilds) AND e.state = com.mrkirby153.giveaways.jpa.GiveawayState.RUNNING ORDER BY e.endsAt")
    fun getNextEnds(guilds: List<String>): List<GiveawayEntity?>
    fun getAllByGuildIdInAndEndsAtIsBeforeAndStateIs(guilds: List<String>, timestamp: Timestamp, state: GiveawayState): List<GiveawayEntity>

    fun getByInteractionUuid(uuid: String): GiveawayEntity?
}