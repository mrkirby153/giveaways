package com.mrkirby153.snowsgivingbot.web;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] spaRoutes = {
        "/",
        "/giveaways/**",
        "/login",
        "/invite"
    };

    @Override
    public void addViewControllers(@NotNull ViewControllerRegistry registry) {
        for (String route : spaRoutes) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }
}
