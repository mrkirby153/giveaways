package com.mrkirby153.giveaways.jpa

import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table


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