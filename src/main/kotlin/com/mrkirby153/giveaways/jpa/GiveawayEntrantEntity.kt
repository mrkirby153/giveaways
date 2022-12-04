package com.mrkirby153.giveaways.jpa

import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

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
}