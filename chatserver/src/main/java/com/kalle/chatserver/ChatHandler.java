package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ChatHandler implements HttpHandler {

    private ArrayList<String> messages = new ArrayList<String>();
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String errorMessage = "";
        int code = 200;
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests (client sent new chat message)
                InputStream input = exchange.getRequestBody();
                String text = new BufferedReader(new InputStreamReader(input,
                                            StandardCharsets.UTF_8))
                                            .lines()
                                            .collect(Collectors.joining("\n"));
                // Cheking if the string text is empty or null before adding it to messages
                if(text != null && !text.isBlank()) {
                    messages.add(text);
                    exchange.sendResponseHeaders(code, -1);
                }else{
                    // Sending an error message if message was empty or null
                    code = 400;
                    errorMessage = "Message was empty";
                }
                input.close();
            } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                // Handle GET request (client wants to see all messages)
                String messageBody = "";
                for (String message : messages){
                    messageBody += message + "\n";
                }
                byte[] bytes = messageBody.getBytes("UTF-8");
                exchange.sendResponseHeaders(code, bytes.length);
                OutputStream stream = exchange.getResponseBody();
                stream.write(bytes);
                stream.close();
            } else {
                // Something we do not support
                code = 400;
                errorMessage = "Not supported";
            }
        } catch (Exception e) {
            // Handle exception
            code = 500;
            errorMessage = "Internal server error";
        }
        if ( code >= 400){
            byte[] bytes = errorMessage.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        }
        

    }

}
