package org.example.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

@Slf4j
@Service
public class TelegramNotifier {

    private final TelegramClient telegramClient;

    public TelegramNotifier(@Value("${telegram.bot.token:}") String botToken) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Retryable(
            includes = { TelegramApiException.class },
            delay = 2000,
            multiplier = 2.0
    )
    public Integer send(String text, Long chatId, Optional<Integer> repliedMessageId) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage(chatId.toString(), text);
        repliedMessageId.ifPresent(sendMessage::setReplyToMessageId);

        org.telegram.telegrambots.meta.api.objects.message.Message sentMessage =
                telegramClient.execute(sendMessage);

        Integer sentMessageId = sentMessage.getMessageId();
        log.info("Sent text to Telegram: '{}' to chat ID: {}, message ID: {}", text, chatId, sentMessageId);
        return sentMessageId;
    }
}
