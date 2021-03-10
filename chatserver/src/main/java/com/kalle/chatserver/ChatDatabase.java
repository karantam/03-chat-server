package com.kalle.chatserver;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.codec.digest.Crypt;

/*
 * ChatDatabase class handles database operations
 */
public class ChatDatabase {

    private Connection dbConnection = null;
    private static ChatDatabase singleton = null;
    // private SecureRandom secureRandom = new SecureRandom();

    public static synchronized ChatDatabase getInstance() {
        if (null == singleton) {
            singleton = new ChatDatabase();
        }
        return singleton;
    }

    private ChatDatabase() {
    }

    /*
     * open method checks if the database exists and if it dosen't calls
     * initializeDatabase method to create it. Then it creates a connection to the
     * database
     */
    public void open(String dbName) throws SQLException {
        File dbFile = new File(dbName);
        boolean exists = dbFile.exists();
        String path = dbFile.getAbsolutePath();
        String database = "jdbc:sqlite:" + path;
        dbConnection = DriverManager.getConnection(database);
        if (!exists) {
            initializeDatabase();
        } else {
            ChatServer.log("Using already existing database");
        }
    }

    /*
     * initializeDatabase method creates the database
     */
    private boolean initializeDatabase() throws SQLException {
        if (null != dbConnection) {
            String registrationDB = "create table registration (user varchar(50) PRIMARY KEY, userpassword varchar(50) NOT NULL, useremail varchar(50) NOT NULL)";
            String chatDB = "create table chat (id INTEGER PRIMARY KEY AUTOINCREMENT, user varchar(50) NOT NULL, usermessage varchar(500) NOT NULL, datetime numeric(50) NOT NULL, channel varchar(50), location varchar(50), temperature varchar(500))";
            String channelDB = "create table channel (channelname varchar(50) PRIMARY KEY)";
            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(registrationDB);
                createStatement.executeUpdate(chatDB);
                createStatement.executeUpdate(channelDB);
                ChatServer.log("DB successfully created");

                return true;
            } catch (SQLException e) {
                ChatServer.log("ERROR: SQLException while creating the database");
            }
        }

        ChatServer.log("DB creation failed");
        return false;
    }

    /*
     * closeDB method closes the database connection
     */
    public void closeDB() throws SQLException {
        if (null != dbConnection) {
            dbConnection.close();
            ChatServer.log("closing database connection");
            dbConnection = null;
        }
    }

    /*
     * setUser method saves a new user into the database if the username isn't
     * already in use. It also encrypts the password pefore saving it.
     */
    public boolean setUser(User user) throws SQLException {
        // Registering a new user
        User existing = getUser(user.getUsername());
        // Cheking if the user name is already in use
        if (existing.getUsername() != null && existing.getUsername().equals(user.getUsername())) {
            ChatServer.log("ERROR: Invalid User");
            return false;
        } else {
            // Securing the password before saving it
            /*
             * byte[] bytes = new byte[13]; secureRandom.nextBytes(bytes); String saltBytes
             * = new String(Base64.getEncoder().encode(bytes)); String salt = "$6$" +
             * saltBytes; String hashedPassword = Crypt.crypt(user.getPassword(), salt);
             */
            String hashedPassword = Crypt.crypt(user.getPassword());
            // Saving the data of the new user into the database
            String setMessageString = "insert into registration VALUES('" + user.getUsername().replace("'", "''")
                    + "','" + hashedPassword + "','" + user.getEmail().replace("'", "''") + "')";
            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(setMessageString);
            } catch (SQLException e) {
                ChatServer.log("ERROR: SQLException while adding user to database");
            }
            return true;
        }
    }

    /*
     * getUser method retrieves user information from the database.
     */
    public User getUser(String user) throws SQLException {
        String getMessagesString = "select user, userpassword, useremail from registration where user =  '"
                + user.replace("'", "''") + "'";
        // String getMessagesString = "select user, userpassword, useremail from
        // registration where user = \"" + user + "\"";
        String username = null;
        String password = null;
        String email = null;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(getMessagesString);
            while (rs.next()) {
                username = rs.getString("user");
                // Now we get a hashed version of the password
                password = rs.getString("userpassword");
                email = rs.getString("useremail");
            }

        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while reading user information from database");
        }
        return new User(username, password, email);

    }

    /*
     * checkUser method checks if the given username and password match the ones in
     * the database.
     */
    public boolean checkUser(String username, String password) throws SQLException {
        User existing = getUser(username);
        if (existing.getUsername().equals(username)
                && existing.getPassword().equals(Crypt.crypt(password, existing.getPassword()))) {
            ChatServer.log("User OK");
            return true;
        } else {
            ChatServer.log("ERROR: Invalid User");
            return false;
        }
    }

    /*
     * setMessage method saves a new message into the database.
     */
    public boolean setMessage(ChatMessage message, String channel) throws SQLException {
        String setMessageString;
        if (channel != null && !channelExists(channel)) {
            ChatServer.log("channel with name " + channel + " doesn't exist");
            return false;
        } else if (channel == null) {
            if (message.getLocation() != null) {
                /*
                 * setMessageString =
                 * "insert into chat (user,usermessage,datetime,channel,location,temperature) VALUES('"
                 * + message.getNick() + "','" + message.getMessage() + "','" +
                 * message.dateAsInt() + "', NULL,'" + message.getLocation() + "','" +
                 * message.getTemperature() + "')";
                 */
                setMessageString = "insert into chat (user,usermessage,datetime,location,temperature) VALUES('"
                        + message.getNick().replace("'", "''") + "','" + message.getMessage().replace("'", "''") + "','"
                        + message.dateAsInt() + "','" + message.getLocation().replace("'", "''") + "','"
                        + message.getTemperature().replace("'", "''") + "')";
                ChatServer.log("TEST");
                ChatServer.log(message.getTemperature());
            } else {
                /*
                 * setMessageString =
                 * "insert into chat (user,usermessage,datetime,channel) VALUES('" +
                 * message.getNick() + "','" + message.getMessage() + "','" +
                 * message.dateAsInt() + "', NULL )";
                 */
                setMessageString = "insert into chat (user,usermessage,datetime) VALUES('"
                        + message.getNick().replace("'", "''") + "','" + message.getMessage().replace("'", "''") + "','"
                        + message.dateAsInt() + "')";
            }
        } else {
            if (message.getLocation() != null) {
                setMessageString = "insert into chat (user,usermessage,datetime,channel,location,temperature) VALUES('"
                        + message.getNick().replace("'", "''") + "','" + message.getMessage().replace("'", "''") + "','"
                        + message.dateAsInt() + "','" + channel.replace("'", "''") + "','"
                        + message.getLocation().replace("'", "''") + "','" + message.getTemperature().replace("'", "''")
                        + "')";
            } else {
                setMessageString = "insert into chat (user,usermessage,datetime,channel) VALUES('"
                        + message.getNick().replace("'", "''") + "','" + message.getMessage().replace("'", "''") + "','"
                        + message.dateAsInt() + "','" + channel.replace("'", "''") + "')";
            }
        }
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(setMessageString);
            ChatServer.log("TESTTEST");
            return true;
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while adding message to database");
        }
        return false;
    }

    /*
     * editMessage method edits a previously saved message in the database
     */
    public boolean editMessage(ChatMessage message, int messageid, String channel) throws SQLException {
        String editMessageString;
        List<String> oldmessage = checkMessage(messageid);
        // added these statements as otherwise vscode complains about duplication of the
        // strings in them
        String updatestatement = "UPDATE chat SET user = '";
        String wherestatement = "' WHERE id = '";
        if (!oldmessage.get(0).equals(message.getNick())) {
            ChatServer.log("User cannot edit other users messages");
            return false;
        } else if (oldmessage.get(1).equals("<deleted>")) {
            ChatServer.log("Messages that have been deleted cannot be edited.");
            return false;
        } else if (channel == null && oldmessage.get(2) != null) {
            ChatServer.log("Message (" + messageid + ") was not found. It might be located on a channel");
            return false;
        } else if (channel != null && !oldmessage.get(2).equals(channel)) {
            ChatServer.log("There is no message (" + messageid + ") on channel: " + channel);
            return false;
        } else if (message.getLocation() != null) {
            editMessageString = updatestatement + message.getNick().replace("'", "''") + "', usermessage = '"
                    + message.getMessage().replace("'", "''") + "<edited>', datetime = '" + message.dateAsInt()
                    + "', location = '" + message.getLocation().replace("'", "''") + "', temperature = '"
                    + message.getTemperature().replace("'", "''") + wherestatement + messageid + "'";
        } else {
            editMessageString = updatestatement + message.getNick().replace("'", "''") + "', usermessage = '"
                    + message.getMessage().replace("'", "''") + "<edited>', datetime = '" + message.dateAsInt()
                    + wherestatement + messageid + "'";
        }
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(editMessageString);
            return true;
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while editing a message in the database");
        }
        return false;
    }

    /*
     * deleteMessage method deletes a previously saved message
     */
    public boolean deleteMessage(ChatMessage message, int messageid, String channel) throws SQLException {
        String editMessageString;
        List<String> oldmessage = checkMessage(messageid);
        if (!oldmessage.get(0).equals(message.getNick())) {
            ChatServer.log("User cannot delete other users messages");
            return false;
        } else if (channel == null && oldmessage.get(2) != null) {
            ChatServer.log("Message (" + messageid + ") was not found. It might be located on a channel");
            return false;
        } else if (channel != null && !oldmessage.get(2).equals(channel)) {
            ChatServer.log("There is no message (" + messageid + ") on channel: " + channel);
            return false;
        } else {
            editMessageString = "UPDATE chat SET user = '" + message.getNick().replace("'", "''")
                    + "', usermessage = '<deleted>', datetime = '" + message.dateAsInt() + "' WHERE id = '" + messageid
                    + "'";
        }
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(editMessageString);
            return true;
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while deleting a message from the database");
        }
        return false;
    }

    /*
     * getMessages method retrieves messages from the databse. It retrieves up to
     * 100 messages that the user hasn't seen yet.
     */
    public List<ChatMessage> getMessages(long since, String channel) throws SQLException {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        // Getting all the messages sent after variable since and sorting them by the
        // sending time in ascending order if since is -1 getting the last 100 messages
        String getMessagesString;
        if (channel != null && !channelExists(channel)) {
            ChatServer.log("channel with name " + channel + " doesn't exist");
            return messages;
        } else if (channel == null) {
            if (since == -1) {
                getMessagesString = "select id, user, usermessage, datetime, location, temperature from (select id, user, usermessage, datetime, location, temperature from chat where channel IS NULL order by datetime DESC limit 100) order by datetime ASC";
            } else {
                getMessagesString = "select id, user, usermessage, datetime, location, temperature from chat where channel IS NULL AND datetime > "
                        + since + " order by datetime ASC";
            }
        } else {
            if (since == -1) {
                getMessagesString = "select id, user, usermessage, datetime, location, temperature from (select id, user, usermessage, datetime, location, temperature from chat where channel = '"
                        + channel.replace("'", "''") + "' order by datetime DESC limit 100) order by datetime ASC";
            } else {
                getMessagesString = "select id, user, usermessage, datetime, location, temperature from chat where channel = '"
                        + channel.replace("'", "''") + "' AND datetime > " + since + " order by datetime ASC";
            }
        }
        String username = null;
        String message = null;
        LocalDateTime sent = null;
        String location = null;
        String temperature = null;
        ChatServer.log("testi1");
        ChatServer.log(channel);
        try (Statement queryStatement = dbConnection.createStatement()) {
            ChatServer.log("testi2");
            ResultSet rs = queryStatement.executeQuery(getMessagesString);
            ChatServer.log("testi3");

            while (rs.next()) {
                username = rs.getString("user");
                // Here we form the message string from the message id number and the message
                // string
                message = "(" + rs.getInt("id") + ") " + rs.getString("usermessage");
                sent = null;
                location = rs.getString("location");
                temperature = rs.getString("temperature");
                ChatMessage chatmessage = new ChatMessage(sent, username, message, location, temperature);
                chatmessage.setSent(rs.getLong("datetime"));
                messages.add(chatmessage);
            }

        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while reading messages from database");
        }
        return messages;

    }

    /*
     * checkMessage method retrieves the username and message from an entry with
     * given id number
     */
    public List<String> checkMessage(int messageid) throws SQLException {
        List<String> messageInfo = new ArrayList<>(3);
        String getMessagesString = "select id, user, usermessage, datetime, channel from chat where id = " + messageid;
        String username = null;
        String message = null;
        String channel = null;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(getMessagesString);

            while (rs.next()) {
                username = rs.getString("user");
                // Here we form the message string from the message id number and the message
                // string
                message = rs.getString("usermessage");
                // message = "(" + rs.getInt("id") + ") " + rs.getString("usermessage");
                channel = rs.getString("channel");
            }

        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while searching a message from database");
        }
        messageInfo.add(0, username);
        messageInfo.add(1, message);
        messageInfo.add(2, channel);
        return messageInfo;

    }

    /*
     * createChannel method creates a new chat channel
     */
    public boolean createChannel(String channel) throws SQLException {

        if (!channelExists(channel)) {
            String setChannel = "insert into channel VALUES('" + channel.replace("'", "''") + "')";
            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(setChannel);
                ChatServer.log("Channel " + channel + " successfully created");
                return true;
            } catch (SQLException e) {
                ChatServer.log("ERROR: SQLException while creating the database");
            }
        } else {
            ChatServer.log("Channel with that name already exists");
        }
        ChatServer.log("channel creation failed");
        return false;
    }

    /*
     * channelExists method checks if a channel already exists
     */
    public boolean channelExists(String channel) throws SQLException {
        String tableExists;
        // if (channel == null){
        // tableExists = "SELECT channelname FROM channel WHERE channelname IS NULL;";
        // } else {
        tableExists = "SELECT channelname FROM channel WHERE channelname = '" + channel.replace("'", "''") + "'";
        // tableExists = "SELECT channelname FROM channel WHERE channelname = \"" +
        // channel + "\"";
        // }
        boolean exists = false;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(tableExists);
            exists = rs.next();
        } catch (SQLException e) {
            ChatServer.log("SQLException while checking if a channel exists.");
        }
        return exists;
    }

    /*
     * getChannels method gets a list of all the channels
     */
    public List<String> getChannels() throws SQLException {
        String getChannelString = "SELECT channelname FROM channel";
        ArrayList<String> channelList = new ArrayList<>();
        String channel = null;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(getChannelString);
            while (rs.next()) {
                channel = rs.getString("channelname");
                channelList.add(channel);
            }
        } catch (SQLException e) {
            ChatServer.log("SQLException while reading channel names from database.");
        }
        return channelList;
    }
}
