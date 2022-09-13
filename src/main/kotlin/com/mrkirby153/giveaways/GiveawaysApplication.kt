package com.mrkirby153.giveaways

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@EnableJpaRepositories(basePackages = ["com.mrkirby153.giveaways.jpa", "com.mrkirby153.giveaways.scheduler.jpa"])
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@SpringBootApplication
class GiveawaysApplication

fun main(args: Array<String>) {
    runApplication<GiveawaysApplication>(*args)
}
