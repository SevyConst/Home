package org.example.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final String chatId;
    private final TelegramClient telegramClient;

    public TelegramNotifier(@Value("${telegram.bot.token:}") String botToken,
                            @Value("${telegram.bot.chat-id:}") String chatId) {
        this.chatId = chatId;
        this.telegramClient = new OkHttpTelegramClient(botToken);

    }

    @Async
    public void send(String message) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(message).build());
            log.info("Sent message '{}' to Telegram", message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message '{}' to Telegram", message, e);
        }
    }
}
