package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONException;
import org.json.JSONObject;

public class AdministrationHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        try {
            ChatServer.log("Request handled in thread " + Thread.currentThread().getId());
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                status = handleAdministrationRequest(exchange);
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
            ChatServer.log("Error in /administration: " + code + " " + statusMessage);
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
     * handleAdministrationRequest method edits or deletes an user
     */
    private List<String> handleAdministrationRequest(HttpExchange exchange) throws JSONException, NumberFormatException,
            IndexOutOfBoundsException, IOException, DateTimeParseException, SQLException {
        // Handle POST requests (client want's to edits or deletes an user)
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
            ChatServer.log(text);
            input.close();
            // Creating a JSONObject from user input
            JSONObject administrationMsg = new JSONObject(text);

            cType = "user";
            String username = hasContentString(administrationMsg, cType);

            cType = "action";
            String action = hasContentString(administrationMsg, cType);

            cType = "userdetails";
            JSONObject userDetails = hasContentJSON(administrationMsg, cType);

            // Getting username from authentication header
            String adminName = exchange.getPrincipal().getUsername();
            // Cheking if the string username or action is empty
            if (!username.isBlank() && !action.isBlank()) {
                status = processAction(username, action, userDetails, adminName);
                code = Integer.parseInt(status.get(0));
                statusMessage = status.get(1);
                if (code < 400) {
                    exchange.sendResponseHeaders(code, -1);
                    ChatServer.log("POST request processed in /administration");
                }
            } else {
                // Sending an error message if action or username was empty or null
                code = 400;
                statusMessage = "Action or username was missing.";
            }
        } else {
            code = 411;
            statusMessage = "Content-Type must be application/json";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    private List<String> processAction(String username, String action, JSONObject userDetails, String adminName)
            throws SQLException{
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";

        if (action.equals("edit")) {
            String newUsername = null;
            String newPassword = null;
            String newEmail = null;
            String newRole = null;
            if (userDetails != null) {
                String cType = "username";
                newUsername = hasContentString(userDetails, cType);
                cType = "password";
                newPassword = hasContentString(userDetails, cType);
                cType = "email";
                newEmail = hasContentString(userDetails, cType);
                cType = "role";
                newRole = hasContentString(userDetails, cType);
                if(newUsername.length() < 3 || newPassword.length() < 5 || newEmail.contains("@")){
                        code = 400;
                        statusMessage = "Name must be at least three characters long, password must be at least five characters long and email must be a valid email address";
                        status.add(0, String.valueOf(code));
                        status.add(1, statusMessage);
                        return status;
                    }
            } else {
                code = 400;
                statusMessage = "Missing user details";
                status.add(0, String.valueOf(code));
                status.add(1, statusMessage);
                return status;
            }
            User newUserDetails = new User(newUsername, newPassword, newEmail);
            status = ChatDatabase.getInstance().editUser(newUserDetails, username, newRole, adminName);
        } else if (action.equals("remove")) {
            status = ChatDatabase.getInstance().deleteUser(username, adminName);
        } else {
            code = 400;
            statusMessage = "Invalid action";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    private String hasContentString(JSONObject object, String content) {
        String value = "";
        if (object.has(content)) {
            value = object.getString(content);
        }
        return value;
    }

    private JSONObject hasContentJSON(JSONObject object, String content) {
        JSONObject value = null;
        if (object.has(content)) {
            value = object.getJSONObject(content);
        }
        return value;
    }
}
