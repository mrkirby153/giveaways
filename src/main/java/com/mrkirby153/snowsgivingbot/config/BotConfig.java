package com.mrkirby153.snowsgivingbot.config;

import com.mrkirby153.botcore.event.EventWaiter;
import com.mrkirby153.snowsgivingbot.services.EventService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BotConfig {

    public static final String GREEN_CHECK = "✅";
    public static final String RED_CROSS = "❌";

    private final String token;
    private final EventService springJdaShim;


    public BotConfig(@Value("${bot.token}") String token, EventService service) {
        this.token = token;
        this.springJdaShim = service;
    }

    @Bean
    public JDA getJda() throws Exception {
        log.info("Starting bot");
        JDABuilder builder = new JDABuilder(AccountType.BOT).setToken(this.token)
            .addEventListeners(springJdaShim);
        return builder.build();
    }

    @Bean
    public EventWaiter eventWaiter(JDA jda) {
        EventWaiter waiter = new EventWaiter();
        jda.addEventListener(waiter);
        return waiter;
    }
}
