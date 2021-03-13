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

/*
 * ChannelHandler class is the handler for channel administration
 */

public class ChannelHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        try {
            ChatServer.log("Request handled in thread " + Thread.currentThread().getId());
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                status = handleChannelCreation(exchange);
                code = Integer.parseInt(status.get(0));
                statusMessage = status.get(1);
            } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                // Handle GET request in method
                status = handleGetRequestFromClient(exchange);
                code = Integer.parseInt(status.get(0));
                statusMessage = status.get(1);
            } else {
                // Something we do not support
                code = 400;
                statusMessage = "Not supported";
            }
        } catch (IOException e) {
            // Handle exception
            code = 500;
            statusMessage = "Error in handling the request: " + e.getMessage();
        } catch (Exception e) {
            // Handle exception
            code = 500;
            statusMessage = "Internal server error: " + e.getMessage();
        }
        if (code >= 400) {
            ChatServer.log("Error in /channel: " + code + " " + statusMessage);
            byte[] bytes = statusMessage.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        } else {
            ChatServer.log(statusMessage);
        }
    }

    /*
     * handleChannelCreation method creates a new chat channel
     */
    private List<String> handleChannelCreation(HttpExchange exchange) throws JSONException, NumberFormatException,
            IndexOutOfBoundsException, IOException, DateTimeParseException, SQLException {
        // Handle POST requests (client want's to create a new channel)
        // The list status is used to deliver status codes and status messages
        List<String> status = new ArrayList<>(2);
        int code;
        String statusMessage = "";
        Headers headers = exchange.getRequestHeaders();
        // Checking that content type and content lenght have been given and that
        // content type is application/json
        int contentLength = 0;
        String contentType = "";
        String cType = "Content-Type";
        if (headers.containsKey(cType)) {
            contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
        } else {
            code = 411;
            statusMessage = "No content length in request";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        }
        if (headers.containsKey(cType)) {
            contentType = headers.get(cType).get(0);
        } else {
            code = 400;
            statusMessage = "No content type in request";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        }
        if (contentType.equalsIgnoreCase("application/json")) {
            InputStream input = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
            ChatServer.log(text);
            input.close();
            // Creating a JSONObject from user input
            JSONObject channelMsg = new JSONObject(text);
            // Getting data out of the created JSONObject
            cType = "channel";
            String channel = hasContentString(channelMsg, cType);
            // Checking if the string channel is empty or null before using it to create a
            // channel
            if (channel != null && !channel.isBlank()) {
                status = ChatDatabase.getInstance().createChannel(channel);
                code = Integer.parseInt(status.get(0));
                statusMessage = status.get(1);
                if (code < 400) {
                    exchange.sendResponseHeaders(code, -1);
                    ChatServer.log("POST request processed in /channel");
                }
            } else {
                // Sending an error message if channel name was empty or null
                code = 400;
                statusMessage = "Channel didn't have a name";
            }
        } else {
            code = 411;
            statusMessage = "Content-Type must be application/json";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * handleGetRequestFromClient method returns a list of channels
     */
    private List<String> handleGetRequestFromClient(HttpExchange exchange)
            throws IOException, IllegalArgumentException, DateTimeException, JSONException, SQLException {
        // Handle GET request (client wants to see what channels exist)
        // The list status is used to deliver status codes and status messages
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        List<String> channelList = null;
        // Getting alist of all the channels from the database
        channelList = ChatDatabase.getInstance().getChannels();
        if (channelList == null || channelList.isEmpty()) {
            // Checking if the channel list is empty
            code = 204;
            statusMessage = "There are no additional channels";
            exchange.sendResponseHeaders(code, -1);
            ChatServer.log("GET request processed in /channel");
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        }
        // Turning the channel list into JSONObjects and then a JSONArray and delivering
        // it to the client
        statusMessage = "Delivering a list of channels to a client";
        JSONArray responseChannels = new JSONArray();
        for (String channel : channelList) {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("channel", channel);
            responseChannels.put(jsonMessage);
        }
        String channelsStr = responseChannels.toString();
        byte[] bytes = channelsStr.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(bytes);
        stream.close();
        ChatServer.log("GET request processed in /channel");
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * hasContentString method returns the value of the desired String from
     * JSONObject or null if the content dosen't exist
     */

    private String hasContentString(JSONObject object, String content) {
        String value = null;
        if (object.has(content)) {
            value = object.getString(content);
        }
        return value;
    }
}
