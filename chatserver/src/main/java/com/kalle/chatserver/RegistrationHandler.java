package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RegistrationHandler implements HttpHandler {
    private ChatAuthenticator auth = null;
    //private String[] user;
    String errorMessage = "";

    public RegistrationHandler(ChatAuthenticator authpar) {
        auth = authpar;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        int code = 200;
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                code = handleUserRegistrationFromClient(exchange);
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
            System.out.println("Error in /registration: " + code + " " + errorMessage);
            byte[] bytes = errorMessage.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        }

    }

    private int handleUserRegistrationFromClient(HttpExchange exchange) throws Exception {
        // Handle POST requests (client sent new username and password)
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
            input.close();
            code = processUser(exchange, text);
        } else {
            code = 411;
            errorMessage = "Content-Type must be text/plain";
            System.out.println(errorMessage);
        }
        return code;
    }

    private int processUser(HttpExchange exchange, String text) throws Exception{
        int code = 200;
        // Adding the username and password to known users
        // Cheking if user input was empty
        if (text != null && !text.isBlank()) {
            //Cheking if username and password were given in Gormat name:passwd
            if (text.contains(":")) {
                String[] user = text.split(":", 2);
                // Chkeing if user name or password was empty
                if (user[0] != null && !user[0].isBlank() && user[1] != null && !user[1].isBlank()) {
                    Boolean adduser = auth.addUser(user[0], user[1]);
                    if (Boolean.TRUE.equals(adduser)) {
                        exchange.sendResponseHeaders(code, -1);
                        System.out.println("Added as user");
                    } else {
                        // Sending an error message if username is already in use
                        code = 403;
                        errorMessage = "Invalid username or password";
                    }
                } else {
                    // Sending an error message if username or password was empty or null
                    code = 400;
                    errorMessage = "Invalid username or password";
                }
            } else {
                // Sending an error message if username and password were entered in wrong form
                code = 400;
                errorMessage = "Give username and password in form username:password";
            }
        }
        return code;
    }
}
