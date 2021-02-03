package com.kalle.chatserver;

import java.time.LocalDateTime;

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
}
