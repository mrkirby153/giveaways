package com.mrkirby153.giveaways.scheduler.message

import kotlinx.serialization.Serializable

@Serializable
data class JobScheduled(val id: Long, val time: Long)

@Serializable
data class JobRescheduled(val id: Long, val time: Long)