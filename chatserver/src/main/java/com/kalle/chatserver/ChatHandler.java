package com.kalle.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/*
 * ChatHandler class handles user requests concerning chat messages
 */
public class ChatHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        try {
            ChatServer.log("Request handled in thread " + Thread.currentThread().getId());
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                status = handleChatMessageFromClient(exchange);
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
            ChatServer.log("Error in /chat: " + code + " " + statusMessage);
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
     * handleChatMessageFromClient method starts processing the message sent by
     * client. It checks if the data is in corect format and then if it is sends it
     * to the processmessage method
     */
    private List<String> handleChatMessageFromClient(HttpExchange exchange) throws JSONException, NumberFormatException,
            IndexOutOfBoundsException, IOException, DateTimeParseException, SQLException {
        // Handle POST requests (client sent a new chat message or wants to edit or
        // delete an old message)
        List<String> status = new ArrayList<>(2);
        int code;
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
            JSONObject chatMsg = new JSONObject(text);
            cType = "user";
            String nickname = hasContentString(chatMsg, cType);
            // Cheking if the JSONObject has a message entry as a deletemessage type action
            // doesn't need it so it might not exist
            cType = "message";
            String message = hasContentString(chatMsg, cType);
            cType = "sent";
            String datestr = hasContentString(chatMsg, cType);
            // Implementing chat channels (if JSONObject contains channel entry it is used
            // otherwise default channel is null)

            cType = "channel";
            String channel = hasContentString(chatMsg, cType);

            // Implementing modifying of sent messages (if JSONObject has action entry it is
            // used otherwise default is an empty string which does nothing) and if
            // JSONObject
            // has messageid entry it is used otherwise default is 0

            cType = "action";
            String action = hasContentString(chatMsg, cType);
            // The String action causes problems if it is null so we change it to an empty
            // string n that case
            if (action == null) {
                action = "";
            }

            cType = "messageid";
            int messageid = hasContentInt(chatMsg, cType);

            cType = "location";
            String location = hasContentString(chatMsg, cType);

            OffsetDateTime odt = OffsetDateTime.parse(datestr);
            LocalDateTime sent = odt.toLocalDateTime();

            // Getting username from authentication header
            String username = exchange.getPrincipal().getUsername();

            String temperature = "";
            if (location != null) {
                temperature = getWeather(location, sent);
            }
            if (temperature.equals("Invalid location")) {
                code = 400;
                statusMessage = temperature;
                status.add(0, String.valueOf(code));
                status.add(1, statusMessage);
                return status;
            }
            // Creating a chatmessage out of user input
            ChatMessage chatmessage = new ChatMessage(sent, nickname, message, location, temperature);
            status = processMessage(chatmessage, channel, action, messageid, username);
            code = Integer.parseInt(status.get(0));
            statusMessage = status.get(1);
            if (code < 400) {
                exchange.sendResponseHeaders(code, -1);
                ChatServer.log("POST request processed in /chat");
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
     * processMessage method turns the data given by user into a chatmessage object
     * and based on action string edits a message, deletes a message or saves a
     * message into the database
     */
    private List<String> processMessage(ChatMessage chatmessage, String channel, String action, int messageid,
            String username) throws SQLException {
        List<String> status = new ArrayList<>(2);
        int code;
        String statusMessage = "";
        // Determining what to do base on action String and message String
        if (action.equals("deletemessage")) {
            status = ChatDatabase.getInstance().deleteMessage(chatmessage, messageid, channel, username);
            code = Integer.parseInt(status.get(0));
            statusMessage = status.get(1);
        } else if (chatmessage.getMessage() == null || chatmessage.getMessage().isBlank()) {
            code = 400;
            statusMessage = "Message was empty";
        } else if (action.equals("editmessage")) {
            status = ChatDatabase.getInstance().editMessage(chatmessage, messageid, channel, username);
            code = Integer.parseInt(status.get(0));
            statusMessage = status.get(1);
        } else if (!action.equals("") && !action.equals("deletemessage") && !action.equals("editmessage")) {
            // Giving an error if action was given but wasn't deletemessage or editmessage
            code = 400;
            statusMessage = "Invalid action";
        } else {
            status = ChatDatabase.getInstance().setMessage(chatmessage, channel, username);
            code = Integer.parseInt(status.get(0));
            statusMessage = status.get(1);
        }
        // Returning data on how the operation went in the form of a status code and a
        // message
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * handleGetRequestFromClient method returns all new chatmessages to the user or
     * the latest 100 chatmessages if there are more than 100 new messages
     */
    private List<String> handleGetRequestFromClient(HttpExchange exchange)
            throws IOException, IllegalArgumentException, DateTimeException, JSONException, SQLException {
        // Handle GET request (client wants to see all messages)
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
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
        // implementing channels (As there is no message body channel name is given in
        // headers)
        String channel = null;
        cType = "channel";
        if (headers.containsKey(cType)) {
            channel = headers.get(cType).get(0);
        }
        messages = ChatDatabase.getInstance().getMessages(messagesSince, channel);
        if (messages == null) {
            code = 400;
            statusMessage = "channel with name " + channel + " doesn't exist";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (messages.isEmpty()) {
            code = 204;
            statusMessage = "There are no new messages to deliver";
            exchange.sendResponseHeaders(code, -1);
            ChatServer.log("GET request processed in /chat");
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        }
        statusMessage = "Delivering " + messages.size() + " messages to client";
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
            jsonmessage.put("location", chatmessage.getLocation());
            jsonmessage.put("temperature", chatmessage.getTemperature());
            responseMessages.put(jsonmessage);
        }
        if (newest != null) {
            String lastModified = newest.format(formatterLast);
            Headers headers2 = exchange.getResponseHeaders();
            headers2.add("Last-Modified", lastModified);
        }
        String messagesstr = responseMessages.toString();
        byte[] bytes = messagesstr.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(bytes);
        stream.close();
        ChatServer.log("GET request processed in /chat");
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * getWeather method returns temperature based on the given location
     */

    private String getWeather(String location, LocalDateTime sent) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
        ZonedDateTime senttime = ZonedDateTime.of(sent, ZoneId.of("UTC"));
        // Setting startime and endtime for the data to narrow down results. end time is
        // the time the message was sent and start time is 3 hours before that. The time
        // window is 3 hours becaus acording to ilmatieteenlaitos.fi temperatures are
        // taken somewhere between once every 3 hours to once every minute depending on
        // the location and the time when the data was collected
        String starttime = senttime.minusHours(3).format(formatter);
        String endtime = senttime.format(formatter);
        String address = "https://opendata.fmi.fi/wfs/fin?service=WFS&version=2.0.0&request=getFeature&storedquery_id=fmi::observations::weather::simple&place="
                + location + "&starttime=" + starttime + "&endtime=" + endtime + "&parameters=t2m&";

        URL url = new URL(address);
        String temperature = "Temperature wasn't available";

        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(10000);
            urlConnection.setConnectTimeout(20000);

            urlConnection.setRequestMethod("GET");
            inputStream = urlConnection.getInputStream();
            // reading data from the url
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String outputLine;
                StringBuilder bld = new StringBuilder();
                // turning the data into a string an saving it
                while ((outputLine = reader.readLine()) != null) {
                    bld.append(outputLine);
                }
                String outputAll = bld.toString();
                String temperatureSeparator1 = "<BsWfs:ParameterValue>";
                String temperatureSeparator2 = "</BsWfs:ParameterValue>";
                // As the data entries are ordered by time locating the last and thus most
                // recent one and cutting of the parts before it
                if (outputAll.contains(temperatureSeparator1)) {
                    String lastoutput = outputAll.substring(outputAll.lastIndexOf(temperatureSeparator1));
                    // Now as there is only one Parametervalue entry left splitting the string so
                    // only the value of the parameterValue entry remains
                    if (lastoutput != null && !lastoutput.isBlank()) {
                        temperature = lastoutput.split(temperatureSeparator1)[1].split(temperatureSeparator2)[0];
                        temperature = temperature + " C";
                    } else {
                        ChatServer.log("Temperature data is not available for that location");
                    }
                } else {
                    ChatServer.log("Temperature data is not available for that location");
                }
            }
        } catch (IOException e) {
            // The most probable cause for this error is that opendata.fmi.fi couldn't find
            // locations matching the one given to it. Probalbly because the location either
            // doesn't exist or was misspelled.
            ChatServer.log("Couldn't find data for that location");
            temperature = "Invalid location";
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
        ChatServer.log(temperature);
        return temperature;
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

    /*
     * hasContentInt method returns the value of the desired integer from JSONObject
     * or 0 if the content dosen't exist
     */
    private int hasContentInt(JSONObject object, String content) {
        int value = 0;
        if (object.has(content)) {
            value = object.getInt(content);
        }
        return value;
    }

}
