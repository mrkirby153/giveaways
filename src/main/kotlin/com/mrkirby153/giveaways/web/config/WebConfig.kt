package com.mrkirby153.giveaways.web.config

import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class WebConfig {

    @Bean
    fun httpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(true)
            .build()
    }
}