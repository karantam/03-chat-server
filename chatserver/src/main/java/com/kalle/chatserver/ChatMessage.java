package com.kalle.chatserver;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/*
 * ChatMessage class creates a chatmessage object containing all the information about that message
 */
public class ChatMessage {
    private LocalDateTime sent;
    private String nick;
    private String message;

    public ChatMessage(LocalDateTime sentpar, String nickpar, String messagepar) {
        this.sent = sentpar;
        this.nick = nickpar;
        this.message = messagepar;
    }

    public LocalDateTime getSent() {
        return this.sent;
    }

    public String getNick() {
        return this.nick;
    }

    public String getMessage() {
        return this.message;
    }

    public ChatMessage getChatMessage() {
        return new ChatMessage(sent, nick, message);
    }

    long dateAsInt() {
        return sent.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    void setSent(long epoch) {
        sent = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
    }
}
