package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatHandler implements HttpHandler {

    private String errorMessage = "";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        int code = 200;
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                code = handleChatMessageFromClient(exchange);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                // Handle GET request in method
                code = handleGetRequestFromClient(exchange);
            } else {
                // Something we do not support
                code = 400;
                errorMessage = "Not supported";
            }
        } catch (IOException e) {
            // Handle exception
            code = 500;
            errorMessage = "Error in handling the request: " + e.getMessage();
        } catch (Exception e) {
            // Handle exception
            code = 500;
            errorMessage = "Internal server error: " + e.getMessage();
        }
        if (code >= 400) {
            ChatServer.log("Error in /chat: " + code + " " + errorMessage);
            byte[] bytes = errorMessage.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        }
    }

    private int handleChatMessageFromClient(HttpExchange exchange) throws JSONException, NumberFormatException,
            IndexOutOfBoundsException, IOException, DateTimeParseException, SQLException {
        // Handle POST requests (client sent new chat message)
        int code = 200;
        Headers headers = exchange.getRequestHeaders();
        int contentLength = 0;
        String contentType = "";
        String cType = "Content-Type";
        if (headers.containsKey(cType)) {
            contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
        } else {
            code = 411;
            return code;
        }
        if (headers.containsKey(cType)) {
            contentType = headers.get(cType).get(0);
        } else {
            code = 400;
            errorMessage = "No content type in request";
            return code;
        }
        if (contentType.equalsIgnoreCase("application/json")) {
            InputStream input = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
            ChatServer.log(text);
            input.close();
            // Creating a JSONObject from user input
            JSONObject chatMsg = new JSONObject(text);
            String user = chatMsg.getString("user");
            String message = chatMsg.getString("message");
            String datestr = chatMsg.getString("sent");
            OffsetDateTime odt = OffsetDateTime.parse(datestr);
            LocalDateTime sent = odt.toLocalDateTime();
            ChatServer.log(chatMsg.toString());
            // Cheking if the string text is empty or null before adding it to messages
            if (message != null && !message.isBlank()) {
                processMessage(sent, user, message);
                exchange.sendResponseHeaders(code, -1);
                ChatServer.log("New message saved");
            } else {
                // Sending an error message if message was empty or null
                code = 400;
                errorMessage = "Message was empty";
                ChatServer.log(errorMessage);
            }
        } else {
            code = 411;
            errorMessage = "Content-Type must be application/json";
            ChatServer.log(errorMessage);
        }
        return code;
    }

    private void processMessage(LocalDateTime sent, String user, String message) throws SQLException {
        // Creating an chatmessage out of user input
        ChatMessage chatmessage = new ChatMessage(sent, user, message);
        // Adding new chatmessage to messages
        ChatDatabase database = ChatDatabase.getInstance("ChatServer.db");
        database.setMessage(chatmessage);
    }

    private int handleGetRequestFromClient(HttpExchange exchange)
            throws IOException, IllegalArgumentException, DateTimeException, JSONException, SQLException {
        // Handle GET request (client wants to see all messages)
        int code = 200;
        List<ChatMessage> messages = null;
        Headers headers = exchange.getRequestHeaders();
        String cType = "If-Modified-Since";
        DateTimeFormatter formatterLast = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss.SSS z", Locale.ENGLISH)
                .withZone(ZoneId.of("GMT"));
        long messagesSince = -1;
        // Cheking if the headers contain If-Modified-Since header
        if (headers.containsKey(cType)) {
            String ifModified = headers.get("If-Modified-Since").get(0);
            ZonedDateTime zonedifModified = ZonedDateTime.parse(ifModified, formatterLast);
            LocalDateTime fromWhichDate = zonedifModified.toLocalDateTime();
            messagesSince = fromWhichDate.toInstant(ZoneOffset.UTC).toEpochMilli();
        }
        ChatDatabase database = ChatDatabase.getInstance("ChatServer.db");
        messages = database.getMessages(messagesSince);

        if (messages == null || messages.isEmpty()) {
            ChatServer.log("No new messages to deliver to client");
            code = 204;
            exchange.sendResponseHeaders(code, -1);
            return code;
        }
        JSONArray responseMessages = new JSONArray();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        LocalDateTime newest = null;
        for (ChatMessage chatmessage : messages) {
            JSONObject jsonmessage = new JSONObject();
            LocalDateTime date = chatmessage.getSent();
            // Messages we get from the database should be ordered by the sending time so
            // only getting the sent time from the last message on the list could also work
            if (newest == null || newest.isBefore(date)) {
                newest = date;
            }
            ZonedDateTime toSend = ZonedDateTime.of(date, ZoneId.of("UTC"));
            String dateText = toSend.format(formatter);
            jsonmessage.put("sent", dateText);
            jsonmessage.put("user", chatmessage.getNick());
            jsonmessage.put("message", chatmessage.getMessage());
            responseMessages.put(jsonmessage);
        }
        if (newest != null) {
            String lastModified = newest.format(formatterLast);
            ChatServer.log("Last-Modified: " + lastModified);
            Headers headers2 = exchange.getResponseHeaders();
            headers2.add("Last-Modified", lastModified);
        }
        ChatServer.log("Delivering " + messages.size() + " messages to client");
        String messagesstr = responseMessages.toString();
        byte[] bytes = messagesstr.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(bytes);
        stream.close();
        return code;
    }

}
