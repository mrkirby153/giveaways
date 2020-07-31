package com.mrkirby153.snowsgivingbot.services;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import java.util.concurrent.CompletableFuture;

public interface ConfirmationService {

    CompletableFuture<Boolean> confirm(Message message, User user);
}
