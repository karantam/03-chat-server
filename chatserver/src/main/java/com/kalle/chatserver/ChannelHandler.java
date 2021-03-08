package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChannelHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String errorMessage = "";
        try {
            ChatServer.log("Request handled in thread " + Thread.currentThread().getId());
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                status = handleChannelCreation(exchange);
                code = Integer.parseInt(status.get(0));
                errorMessage = status.get(1);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                // Handle GET request in method
                status = handleGetRequestFromClient(exchange);
                code = Integer.parseInt(status.get(0));
                errorMessage = status.get(1);
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
            ChatServer.log("Error in /channel: " + code + " " + errorMessage);
            byte[] bytes = errorMessage.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        }
    }

    /*
     * handleChannelCreation method creates a new chat channel
     */
    private List<String> handleChannelCreation(HttpExchange exchange) throws JSONException, NumberFormatException,
            IndexOutOfBoundsException, IOException, DateTimeParseException, SQLException {
        // Handle POST requests (client sent new chat message)
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String errorMessage = "";
        Headers headers = exchange.getRequestHeaders();
        int contentLength = 0;
        String contentType = "";
        String cType = "Content-Type";
        if (headers.containsKey(cType)) {
            contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
        } else {
            code = 411;
            status.add(0, String.valueOf(code));
            status.add(1, errorMessage);
            return status;
        }
        if (headers.containsKey(cType)) {
            contentType = headers.get(cType).get(0);
        } else {
            code = 400;
            errorMessage = "No content type in request";
            status.add(0, String.valueOf(code));
            status.add(1, errorMessage);
            return status;
        }
        if (contentType.equalsIgnoreCase("application/json")) {
            InputStream input = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
            ChatServer.log(text);
            input.close();
            // Creating a JSONObject from user input
            JSONObject chatMsg = new JSONObject(text);
            String channel = chatMsg.getString("channel");
            // Implementin chat channels
            ChatServer.log(channel);
            // Cheking if the string text is empty or null before adding it to messages
            if (channel != null && !channel.isBlank()) {
                ChatDatabase.getInstance().createChannel(channel);
                exchange.sendResponseHeaders(code, -1);
                ChatServer.log("New channel created");
            } else {
                // Sending an error message if channel name was empty or null
                code = 400;
                errorMessage = "Channel didn't have a name";
                ChatServer.log(errorMessage);
            }
        } else {
            code = 411;
            errorMessage = "Content-Type must be application/json";
            ChatServer.log(errorMessage);
        }
        status.add(0, String.valueOf(code));
        status.add(1, errorMessage);
        return status;
    }

    /*
     * handleGetRequestFromClient method returns a list of channels
     */
    private List<String> handleGetRequestFromClient(HttpExchange exchange)
            throws IOException, IllegalArgumentException, DateTimeException, JSONException, SQLException {
        // Handle GET request (client wants to see all messages)
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String errorMessage = "";
        List<String> channelList = null;
        channelList = ChatDatabase.getInstance().getChannels();
        if (channelList == null || channelList.isEmpty()) {
            ChatServer.log("There are no additional channels");
            code = 204;
            exchange.sendResponseHeaders(code, -1);
            status.add(0, String.valueOf(code));
            status.add(1, errorMessage);
            return status;
        }
        JSONArray responseChannels = new JSONArray();
        for (String channel : channelList) {
            JSONObject jsonmessage = new JSONObject();
            jsonmessage.put("channel", channel);
            responseChannels.put(jsonmessage);
        }
        ChatServer.log("Delivering a list of channels to client");
        String channelsstr = responseChannels.toString();
        byte[] bytes = channelsstr.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(bytes);
        stream.close();
        status.add(0, String.valueOf(code));
        status.add(1, errorMessage);
        return status;
    }

}

