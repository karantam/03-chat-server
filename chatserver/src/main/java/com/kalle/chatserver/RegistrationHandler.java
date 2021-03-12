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
        String statusMessage = "";
        try {
            ChatServer.log("Request handled in thread " + Thread.currentThread().getId());
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                status = handleUserRegistrationFromClient(exchange);
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
            ChatServer.log("Error in /registration: " + code + " " + statusMessage);
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
     * handleUserRegistrationFromClient methods starts processing the registration
     * request sent by client. It checks if the data is in corect format and then if
     * it is sends it to the processUser method
     */
    private List<String> handleUserRegistrationFromClient(HttpExchange exchange)
            throws NumberFormatException, IndexOutOfBoundsException, IOException, JSONException, SQLException {
        // Handle POST requests (client sent new username and password)
        List<String> status = new ArrayList<>(2);
        int code;
        //int code = 200;
        String statusMessage = "";
        Headers headers = exchange.getRequestHeaders();
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
            input.close();
            status = processUser(exchange, text);
            code = Integer.parseInt(status.get(0));
            statusMessage = status.get(1);
        } else {
            code = 411;
            statusMessage = "Content-Type must be application/json";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
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
        int code;
        //int code = 200;
        String statusMessage = "";
        // Adding the username and password to known users
        // creating a JSONObject from the user input
        JSONObject registrationMsg = new JSONObject(text);
        // Cheking if any of the user inputs fields was empty
        String cType = "username";
        String username = hasContentString(registrationMsg, cType);
        cType = "password";
        String password = hasContentString(registrationMsg, cType);
        cType = "email";
        String email = hasContentString(registrationMsg, cType);
        if (username != null && !username.isBlank() && password != null && !password.isBlank() && email != null
                && !email.isBlank()) {
            User user = new User(username, password, email);
            status = auth.addUser(user);
            code = Integer.parseInt(status.get(0));
            statusMessage = status.get(1);
            if (code < 400) {
                exchange.sendResponseHeaders(code, -1);
                /*String statusMessage = "New user has been registered";
                byte[] bytes = statusMessage.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(code, bytes.length);*/
                //statusMessage = "New user has been registered";
            } 
            /*else {
                // Sending an error message if username is already in use
                code = 403;
                statusMessage = "Invalid user credentials";
            }*/
        } else {
            // Sending an error message if username, password or email was empty or null
            code = 400;
            statusMessage = "Invalid user credentials";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * hasContentString method returns the value of the desired String from JSONObject or null if the content dosen't exist
     */

    private String hasContentString(JSONObject object ,String content) {
        String value = null;
        if (object.has(content)) {
            value = object.getString(content);
        }
        return value;
    }
}
