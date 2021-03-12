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
    private String location;
    private String temperature;

    public ChatMessage(LocalDateTime sentpar, String nickpar, String messagepar, String locationpar,
            String temperaturepar) {
        this.sent = sentpar;
        this.nick = nickpar;
        this.message = messagepar;
        this.location = locationpar;
        this.temperature = temperaturepar;
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

    public String getLocation() {
        return this.location;
    }

    public String getTemperature() {
        return this.temperature;
    }

    public ChatMessage getChatMessage() {
        return new ChatMessage(sent, nick, message, location, temperature);
    }

    long dateAsInt() {
        return sent.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    void setSent(long epoch) {
        sent = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
    }
}
