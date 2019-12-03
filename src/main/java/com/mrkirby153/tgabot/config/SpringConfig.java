package com.mrkirby153.tgabot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring configuration class
 */
@Configuration
@EnableScheduling
@EnableAsync
@EnableJpaAuditing
public class SpringConfig {
}
