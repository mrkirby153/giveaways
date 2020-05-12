package com.mrkirby153.snowsgivingbot.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpConfig {

    @Bean
    public OkHttpClient httpClient() {
        return new OkHttpClient.Builder()
            .followRedirects(true)
            .build();
    }
}
