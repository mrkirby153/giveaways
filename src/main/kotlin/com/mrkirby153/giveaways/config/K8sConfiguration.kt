package com.mrkirby153.giveaways.config

import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.util.Config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class K8sConfiguration {
    @Bean
    fun k8sClient(): ApiClient = Config.defaultClient().apply {
        setHttpClient(this.httpClient.newBuilder().readTimeout(0, TimeUnit.SECONDS).build())
    }
}