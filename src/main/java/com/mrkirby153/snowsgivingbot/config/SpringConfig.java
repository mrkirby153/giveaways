package com.mrkirby153.snowsgivingbot.config;

import lombok.Getter;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Spring configuration class
 */
@Configuration
@EnableScheduling
@EnableAsync(proxyTargetClass = true)
@EnableJpaAuditing
public class SpringConfig implements AsyncConfigurer {

    @Getter
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();

    public SpringConfig() {
        threadPoolTaskExecutor.setCorePoolSize(10);
        threadPoolTaskExecutor.setMaxPoolSize(10);
    }

    @Override
    public Executor getAsyncExecutor() {
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor;
    }
}
