package com.mrkirby153.giveaways.scheduler.jpa

import com.mrkirby153.giveaways.jpa.AutoIncrementingJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.sql.Timestamp
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@Table(name = "jobs")
@Entity
class JobEntity(
    @Column(name = "class")
    val backingClass: String,
    var data: String?,
    val queue: String,
    @Column(name = "run_at")
    val runAt: Timestamp
) : AutoIncrementingJpaEntity<Long>()

interface JobRepository : JpaRepository<JobEntity, Long>