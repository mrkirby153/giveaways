package com.mrkirby153.giveaways

import me.mrkirby153.kcutils.spring.coroutine.config.EnableTransactionalCoroutines
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@EnableJpaRepositories(basePackages = ["com.mrkirby153.giveaways.jpa"])
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableTransactionalCoroutines
class GiveawaysApplication

fun main(args: Array<String>) {
    runApplication<GiveawaysApplication>(*args)
}
