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
        String errorMessage = "";
        try {
            ChatServer.log("Request handled in thread " + Thread.currentThread().getId());
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // Handle POST requests in method
                status = handleChatMessageFromClient(exchange);
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
            ChatServer.log("Error in /chat: " + code + " " + errorMessage);
            byte[] bytes = errorMessage.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream stream = exchange.getResponseBody();
            stream.write(bytes);
            stream.close();
        }
    }

    /*
     * handleChatMessageFromClient method starts processing the message sent by
     * client. It checks if the data is in corect format and then if it is sends it
     * to the processmessage method
     */
    private List<String> handleChatMessageFromClient(HttpExchange exchange) throws JSONException, NumberFormatException,
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
            String user = chatMsg.getString("user");
            String message = null;
            cType = "message";
            // Cheking if the JSONObject has message entry as a deletemessage type action
            // dosen't need it so it might not exist
            if (chatMsg.has(cType)) {
                message = chatMsg.getString(cType);
            }
            String datestr = chatMsg.getString("sent");
            // Implementing chat channels (if JSONObject contains channel entry it is used
            // otherwise default channel is null)
            String channel = null;
            cType = "channel";
            if (chatMsg.has(cType)) {
                channel = chatMsg.getString(cType);
            }
            // Implementing modifying of sent messages (if JSONObject has action entry it is
            // used otherwise default is string null(String to avoid NullPointerexception)
            // which does nothing) and if JSONObject has messageid entry it is used
            // otherwise default is 0
            String action = "null";
            cType = "action";
            if (chatMsg.has(cType)) {
                action = chatMsg.getString(cType);
            }
            int messageid = 0;
            cType = "messageid";
            if (chatMsg.has(cType)) {
                messageid = Integer.parseInt(chatMsg.getString(cType));
            }
            String location = null;
            cType = "location";
            if (chatMsg.has(cType)) {
                location = chatMsg.getString(cType);
            }
            OffsetDateTime odt = OffsetDateTime.parse(datestr);
            LocalDateTime sent = odt.toLocalDateTime();
            ChatServer.log(chatMsg.toString());
            // Cheking if the string text is empty or null before adding it to messages
            if (action.equals("deletemessage") || (message != null && !message.isBlank())) {
                boolean success = processMessage(sent, user, message, channel, action, messageid, location);
                if (success) {
                    exchange.sendResponseHeaders(code, -1);
                    ChatServer.log("New message saved");
                } else {
                    code = 400;
                    errorMessage = "There was an error while saving message to the database";
                    ChatServer.log(errorMessage);
                }
            } else {
                // Sending an error message if message was empty or null
                code = 400;
                errorMessage = "Message was empty";
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
     * processMessage method turns the data given by user into a chatmessage object
     * and based on action string edits a message, deletes a message or saves a
     * message into the database
     */
    private boolean processMessage(LocalDateTime sent, String user, String message, String channel, String action,
            int messageid, String location) throws SQLException, IOException {
        boolean success = false;
        String temperature = null;
        if (location != null) {
            ChatServer.log("Getting temperature");
            temperature = getWeather(location, sent);
        }
        // Creating an chatmessage out of user input
        ChatMessage chatmessage = new ChatMessage(sent, user, message, location, temperature);
        // Determining what to do base on action String
        if (action.equals("editmessage")) {
            success = ChatDatabase.getInstance().editMessage(chatmessage, messageid, channel);
            ChatServer.log("Message edited");
        } else if (action.equals("deletemessage")) {
            success = ChatDatabase.getInstance().deleteMessage(chatmessage, messageid, channel);
            ChatServer.log("Message deleted");
        } else {
            success = ChatDatabase.getInstance().setMessage(chatmessage, channel);
            ChatServer.log("New message saved");
        }
        // Returning if operation was a success
        return success;
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
        String errorMessage = "";
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
        if (messages == null || messages.isEmpty()) {
            ChatServer.log("No new messages to deliver to client");
            code = 204;
            exchange.sendResponseHeaders(code, -1);
            status.add(0, String.valueOf(code));
            status.add(1, errorMessage);
            return status;
        }
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
            ChatServer.log("Last-Modified: " + lastModified);
            Headers headers2 = exchange.getResponseHeaders();
            headers2.add("Last-Modified", lastModified);
        }
        ChatServer.log("Delivering " + messages.size() + " messages to client");
        String messagesstr = responseMessages.toString();
        byte[] bytes = messagesstr.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(bytes);
        stream.close();
        status.add(0, String.valueOf(code));
        status.add(1, errorMessage);
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
        // window is 3 hours becaus acording to ilmatiettenlaitos.fi temperatures are
        // taken somewhere between once every 3 hours to once every minute depending on
        // the location and time of the data
        String starttime = senttime.minusHours(3).format(formatter);
        String endtime = senttime.format(formatter);
        String address = "http://opendata.fmi.fi/wfs/fin?service=WFS&version=2.0.0&request=getFeature&storedquery_id=fmi::observations::weather::simple&place="
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
            // urlConnection.setDoOutput(true);
            // urlConnection.setDoInput(true);

            // urlConnection.setRequestProperty("Content-Type", "application/json");

            inputStream = urlConnection.getInputStream();
            // reading data fromthe url
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
                    ChatServer.log(lastoutput);
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
            e.printStackTrace();
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

}
