package com.mrkirby153.giveaways.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository


/**
 * Entity representing a role that has permission to start giveaways
 */
@Table(name = "giveaway_roles")
@Entity
class GiveawayRoleEntity(
    @Column(name = "guild")
    val guildId: String,
    @Column(name = "role_id")
    val roleId: String
) : AutoIncrementingJpaEntity<Long>() {
}

interface GiveawayRoleRepository : JpaRepository<GiveawayRoleEntity, Long> {

}