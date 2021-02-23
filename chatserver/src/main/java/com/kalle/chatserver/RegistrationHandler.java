package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONException;
import org.json.JSONObject;

/*
 * RegistrationHandler class handles user requests concerning registration
 */
public class RegistrationHandler implements HttpHandler {
    private ChatAuthenticator auth = null;

    public RegistrationHandler(ChatAuthenticator authpar) {
        auth = authpar;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String errorMessage = "";
        try {
            ChatServer.log("Request handled in thread " + Thread.currentThread().getId());
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                status = handleUserRegistrationFromClient(exchange);
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
            ChatServer.log("Error in /registration: " + code + " " + errorMessage);
            byte[] bytes = errorMessage.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        }

    }

    /*
     * handleUserRegistrationFromClient methods starts processing the registration
     * request sent by client. It checks if the data is in corect format and then if
     * it is sends it to the processUser method
     */
    private List<String> handleUserRegistrationFromClient(HttpExchange exchange)
            throws NumberFormatException, IndexOutOfBoundsException, IOException, JSONException, SQLException {
        // Handle POST requests (client sent new username and password)
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
            input.close();
            status = processUser(exchange, text);
            code = Integer.parseInt(status.get(0));
            errorMessage = status.get(1);
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
     * processUser method checks if the given username is already in use and if it
     * isn't turns the data given by user into a user object and saves it into the
     * database. It also checks if the user data is empty or null before processing
     * it.
     */
    private List<String> processUser(HttpExchange exchange, String text)
            throws JSONException, IOException, SQLException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String errorMessage = "";
        // Adding the username and password to known users
        // creating a JSONObject from the user input
        JSONObject registrationMsg = new JSONObject(text);
        // Cheking if any of the user inputs fields was empty
        String username = registrationMsg.getString("username");
        String password = registrationMsg.getString("password");
        String email = registrationMsg.getString("email");
        if (username != null && !username.isBlank() && password != null && !password.isBlank() && email != null
                && !email.isBlank()) {
            User user = new User(username, password, email);
            Boolean adduser = auth.addUser(user);
            if (Boolean.TRUE.equals(adduser)) {
                exchange.sendResponseHeaders(code, -1);
                ChatServer.log("Added as user");
            } else {
                // Sending an error message if username is already in use
                code = 403;
                errorMessage = "Invalid user credentials";
            }
        } else {
            // Sending an error message if username, password or email was empty or null
            code = 400;
            errorMessage = "Invalid user credentials";
        }
        status.add(0, String.valueOf(code));
        status.add(1, errorMessage);
        return status;
    }
}
