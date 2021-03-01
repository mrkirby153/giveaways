package com.mrkirby153.snowsgivingbot.web;

import com.mrkirby153.snowsgivingbot.web.services.JwtService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
@AllArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("Registering stomp endpoint /gateway");
        registry.addEndpoint("/gateway").setAllowedOrigins("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor
                    .getAccessor(message, StompHeaderAccessor.class);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String jwtToken = accessor.getPasscode();
                    try {
                        if (jwtToken != null && !jwtToken.isEmpty() && !jwtService
                            .isExpired(jwtToken)) {
                            DiscordUser du = jwtService.getUser(jwtToken);
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                du, null);
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            accessor.setUser(authToken);
                            log.debug("Connection opened with user {}", du);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
