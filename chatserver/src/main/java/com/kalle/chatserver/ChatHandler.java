package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ChatHandler implements HttpHandler {

    private String errorMessage = "";

    private ArrayList<String> messages = new ArrayList<String>();

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
            System.out.println("Error in /chat: " + code + " " + errorMessage);
            byte[] bytes = errorMessage.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        }
    }

    private int handleChatMessageFromClient(HttpExchange exchange) throws Exception {
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
        if (contentType.equalsIgnoreCase("text/plain")) {
            InputStream input = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
            System.out.println(text);
            input.close();
            // Cheking if the string text is empty or null before adding it to messages
            if (text != null && !text.isBlank()) {
                processMessage(text);
                exchange.sendResponseHeaders(code, -1);
                System.out.println("New message saved");
            } else {
                // Sending an error message if message was empty or null
                code = 400;
                errorMessage = "Message was empty";
                System.out.println(errorMessage);
            }
        } else {
            code = 411;
            errorMessage = "Content-Type must be text/plain";
            System.out.println(errorMessage);
        }
        return code;
    }

    private void processMessage(String text) {
        // Adding the string to messages
        messages.add(text);
    }

    private int handleGetRequestFromClient(HttpExchange exchange) throws Exception {
        // Handle GET request (client wants to see all messages)
        int code = 200;
        if (messages.isEmpty()) {
            System.out.println("No new messages to deliver to client");
            code = 204;
            exchange.sendResponseHeaders(code, -1);
            return code;
        }
        String messageBody = "";
        for (String message : messages) {
            messageBody += message + "\n";
        }
        System.out.println("Delivering " + messages.size() + " messages to client");
        byte[] bytes = messageBody.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(bytes);
        stream.close();
        return code;
    }

}
