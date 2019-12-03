package com.mrkirby153.tgabot;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.AttachmentOption;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@RequiredArgsConstructor
public class EmptyMessageAction implements MessageAction {

    private final JDA jda;
    private final Message message;
    private MessageChannel channel;

    @Nonnull
    @Override
    public JDA getJDA() {
        return jda;
    }

    @Override
    public void queue(@Nullable Consumer<? super Message> success,
        @Nullable Consumer<? super Throwable> failure) {
        if (success != null) {
            success.accept(message);
        }
    }

    @Override
    public Message complete(boolean shouldQueue) throws RateLimitedException {
        return message;
    }

    @Nonnull
    @Override
    public CompletableFuture<Message> submit(boolean shouldQueue) {
        return CompletableFuture.completedFuture(message);
    }

    @Nonnull
    @Override
    public MessageAction setCheck(@Nullable BooleanSupplier checks) {
        return this;
    }

    @Nonnull
    @Override
    public MessageChannel getChannel() {
        return channel;
    }

    @Override
    public boolean isEmpty() {
        return message.getContentRaw().isEmpty();
    }

    @Override
    public boolean isEdit() {
        return false;
    }

    @Nonnull
    @Override
    public MessageAction apply(@Nullable Message message) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction tts(boolean isTTS) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction reset() {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction nonce(@Nullable String nonce) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction content(@Nullable String content) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction embed(@Nullable MessageEmbed embed) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction append(@Nullable CharSequence csq, int start, int end) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction append(char c) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction addFile(@Nonnull InputStream data, @Nonnull String name,
        @Nonnull AttachmentOption... options) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction addFile(@Nonnull File file, @Nonnull String name,
        @Nonnull AttachmentOption... options) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction clearFiles() {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction clearFiles(@Nonnull BiConsumer<String, InputStream> finalizer) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction clearFiles(@Nonnull Consumer<InputStream> finalizer) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction override(boolean bool) {
        return this;
    }
}
