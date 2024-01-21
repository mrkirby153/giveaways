package com.mrkirby153.giveaways.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

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