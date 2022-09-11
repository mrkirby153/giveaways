package com.mrkirby153.giveaways

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories

@EnableJpaRepositories(basePackages = ["com.mrkirby153.giveaways.jpa", "com.mrkirby153.giveaways.scheduler.jpa"])
@EnableJpaAuditing
@SpringBootApplication
class GiveawaysApplication

fun main(args: Array<String>) {
    runApplication<GiveawaysApplication>(*args)
}
