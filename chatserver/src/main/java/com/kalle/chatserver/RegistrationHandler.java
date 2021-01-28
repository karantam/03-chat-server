package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RegistrationHandler implements HttpHandler {
    private ChatAuthenticator auth = null;
    private String[] user;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String errorMessage = "";
        int code = 200;
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests (client sent username and passworde)
                InputStream input = exchange.getRequestBody();
                String text = new BufferedReader(new InputStreamReader(input,
                                            StandardCharsets.UTF_8))
                                            .lines()
                                            .collect(Collectors.joining("\n"));
                // Cheking if the string text is empty or null before splitting it
                if (text.contains(":")){
                    user = text.split(":", 2);
                    if (user[0] != null && !user[0].isBlank() && user[1] != null && !user[1].isBlank()) {
                        Boolean adduser = auth.addUser(user[0], user[1]);
                        if (Boolean.TRUE.equals(adduser)){
                            exchange.sendResponseHeaders(code, -1);
                        } else{
                        // Sending an error message if username is already in use
                        code = 403;
                        errorMessage = "User already registered";
                        }
                    }else{
                        // Sending an error message if username or password was empty or null
                        code = 400;
                        errorMessage = "Username or password was empty";
                    }
                }else{
                    // Sending an error message if username and password were entered in wrong form
                    code = 400;
                    errorMessage = "Give username and password in form username:password";
                }
                input.close();
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

    public RegistrationHandler(ChatAuthenticator authpar){
        auth = authpar;
    }
}
