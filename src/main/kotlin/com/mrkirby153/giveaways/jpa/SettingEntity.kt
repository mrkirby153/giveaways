package com.mrkirby153.giveaways.jpa

import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@Entity
@Table(name = "settings")
class SettingEntity(
    val guild: String,
    @Column(name = "`key`")
    val key: String,
    @Column(name = "`value`")
    val value: String
) : AutoIncrementingJpaEntity<Long>() {
}

interface SettingRepository : JpaRepository<SettingEntity, Long> {

}