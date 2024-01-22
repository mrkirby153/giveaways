package com.mrkirby153.giveaways.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Entity representing an entrant into a giveaway
 */
@Table(name = "entrants")
@Entity
@LazyEntity
class GiveawayEntrantEntity(
    @Column(name = "user_id")
    val userId: String
) : AutoIncrementingJpaEntity<Long>() {

    @ManyToOne
    @JoinColumn(name = "giveaway_id")
    lateinit var giveaway: GiveawayEntity

    constructor(giveaway: GiveawayEntity, userId: String) : this(userId) {
        this.giveaway = giveaway
    }

}


interface EntrantRepository : JpaRepository<GiveawayEntrantEntity, Long> {

    fun existsByGiveawayAndUserId(giveaway: GiveawayEntity, userId: String): Boolean

    fun getAllByGiveaway(giveaway: GiveawayEntity): List<GiveawayEntrantEntity>
}