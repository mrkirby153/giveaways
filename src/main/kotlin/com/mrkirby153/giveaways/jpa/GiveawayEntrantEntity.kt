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
class GiveawayEntrantEntity(
    @ManyToOne
    @JoinColumn(name = "giveaway_id")
    val giveaway: GiveawayEntity,
    @Column(name = "user_id")
    val userId: String
) : AutoIncrementingJpaEntity<Long>()


interface EntrantRepository : JpaRepository<GiveawayEntrantEntity, Long> {

}