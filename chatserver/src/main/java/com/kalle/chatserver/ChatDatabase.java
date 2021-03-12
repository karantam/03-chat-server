package com.kalle.chatserver;

import java.io.File;
//import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
//import java.util.Base64;
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
            String registrationDB = "create table registration (user varchar(50) PRIMARY KEY, userpassword varchar(50) NOT NULL, useremail varchar(50) NOT NULL, role varchar(50) NOT NULL)";
            String chatDB = "create table chat (id INTEGER PRIMARY KEY AUTOINCREMENT, username varchar(50) NOT NULL, nickname varchar(50) NOT NULL, usermessage varchar(500) NOT NULL, datetime numeric(50) NOT NULL, channel varchar(50), location varchar(50), temperature varchar(500))";
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
        } else if (!usersExists()){
            // Securing the password before saving it
            String hashedPassword = Crypt.crypt(user.getPassword());
            // Saving the data of the new user into the database
            String setMessageString = "insert into registration VALUES('" + user.getUsername().replace("'", "''")
                    + "','" + hashedPassword + "','" + user.getEmail().replace("'", "''") + "','admin')";
            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(setMessageString);
                ChatServer.log("New Admin user created");
            } catch (SQLException e) {
                ChatServer.log("ERROR: SQLException while adding user to database");
            }
            return true;
        }else {
            // Securing the password before saving it
            /*
             * byte[] bytes = new byte[13]; secureRandom.nextBytes(bytes); String saltBytes
             * = new String(Base64.getEncoder().encode(bytes)); String salt = "$6$" +
             * saltBytes; String hashedPassword = Crypt.crypt(user.getPassword(), salt);
             */
            String hashedPassword = Crypt.crypt(user.getPassword());
            // Saving the data of the new user into the database
            String setMessageString = "insert into registration VALUES('" + user.getUsername().replace("'", "''")
                    + "','" + hashedPassword + "','" + user.getEmail().replace("'", "''") + "','user')";
            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(setMessageString);
                ChatServer.log("New normal user created");
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
     * usersExists method checks if there are any users registered. It returns true if there are users and false if there are no users
     */
    public boolean usersExists() throws SQLException {
        String getMessagesString = "select user from registration limit 1";
        boolean value = false;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(getMessagesString);
            value = rs.next();

        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while cheking if there are any users already registered");
        }
        return value;
    }

    /*
     * isAdmin method checks if the user is an admin.
     */
    public boolean isAdmin(String user) throws SQLException {
        String getMessagesString = "select role from registration where user = '" + user.replace("'", "''") + "'";
        boolean value = false;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(getMessagesString);
            String role = rs.getString("role");
            if (role.equals("admin")){
                value = true;
            }

        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while cheking if user is an admin");
        }
        return value;
    }

    /*
     * anotherAdminExists method checks that the given user is not the last admin.
     */
    public boolean anotherAdminExists(String username) throws SQLException {
        String getMessagesString = "select role from (select role from registration where user != '" + username.replace("'", "''") + "') where role = 'admin'";
        boolean value = false;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(getMessagesString);
            value = rs.next();

        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while cheking if the user is the last admin");
        }
        return value;
    }

    /*
     * editUser method edits a registerd user
     */
    public List<String> editUser(User newuser, String oldusername, String newrole, String adminname) throws SQLException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        String editMessageString;
        if (!isAdmin(adminname)) {
            code = 403;
            statusMessage = "Only admins can edit users";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (!getUser(oldusername).getUsername().equals(oldusername)) {
            code = 400;
            statusMessage = "That user doesn't exist";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (newrole.equals("user")){
            // We check that we are not turning the last admin into a normal user
            if (anotherAdminExists(oldusername)){
                // Securing the password before saving it
                String hashedPassword = Crypt.crypt(newuser.getPassword());
                editMessageString = "UPDATE registration SET nickname = '" + newuser.getUsername().replace("'", "''") + "', userpassword = '"
                        + hashedPassword + ", useremail = '" + newuser.getEmail().replace("'", "''")
                        + "', role = '" + newrole + "' WHERE user = '" + oldusername + "'";
            } else {
                code = 403;
                statusMessage = "You cannot turn the last admin into a normal user";
                status.add(0, String.valueOf(code));
                status.add(1, statusMessage);
                return status;
            }
        } else {
            // Securing the password before saving it
            String hashedPassword = Crypt.crypt(newuser.getPassword());
            editMessageString = "UPDATE registration SET nickname = '" + newuser.getUsername().replace("'", "''") + "', userpassword = '"
                    + hashedPassword + ", useremail = '" + newuser.getEmail().replace("'", "''")
                    + "', role = '" + newrole + "' WHERE user = '" + oldusername + "'";
        }
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(editMessageString);
            if (updateMessages(oldusername, newuser.getUsername())){
                statusMessage = "User was edited and users old messages were updated";
            } else {
                statusMessage = "User was edited but users old messages couldn't be updated";
            }
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while editing an user in the database");
            code = 400;
            statusMessage = "An error occurred while trying to edit an user";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * deleteUser method deletes a registerd user
     */
    public List<String> deleteUser(String username, String adminname) throws SQLException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        String editMessageString;
        if (!isAdmin(adminname)) {
            code = 403;
            statusMessage = "Only admins can delete users";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (!getUser(username).getUsername().equals(username)) {
            code = 400;
            statusMessage = "That user doesn't exist";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        }  else {
            if (anotherAdminExists(username)){
                editMessageString = "DELETE FROM registration WHERE user = '" + username.replace("'", "''") + "'";
            } else {
                code = 403;
                statusMessage = "You cannot delete the last admin";
                status.add(0, String.valueOf(code));
                status.add(1, statusMessage);
                return status;
            }
        }
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(editMessageString);
            statusMessage = "User was deleted";
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while deleting an user from the database");
            code = 400;
            statusMessage = "An error occurred while trying to delete an user";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * setMessage method saves a new message into the database.
     */
    public List<String> setMessage(ChatMessage message, String channel, String username) throws SQLException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        String setMessageString;
        if (channel != null && !channelExists(channel)) {
            code = 400;
            statusMessage = "channel with name " + channel + " doesn't exist";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (channel == null) {
            if (message.getLocation() != null) {
                /*
                 * setMessageString =
                 * "insert into chat (user,usermessage,datetime,channel,location,temperature) VALUES('"
                 * + message.getNick() + "','" + message.getMessage() + "','" +
                 * message.dateAsInt() + "', NULL,'" + message.getLocation() + "','" +
                 * message.getTemperature() + "')";
                 */
                setMessageString = "insert into chat (username,nickname,usermessage,datetime,location,temperature) VALUES('" + username.replace("'", "''") + "','"
                        + message.getNick().replace("'", "''") + "','" + message.getMessage().replace("'", "''") + "','"
                        + message.dateAsInt() + "','" + message.getLocation().replace("'", "''") + "','"
                        + message.getTemperature().replace("'", "''") + "')";
            } else {
                /*
                 * setMessageString =
                 * "insert into chat (user,usermessage,datetime,channel) VALUES('" +
                 * message.getNick() + "','" + message.getMessage() + "','" +
                 * message.dateAsInt() + "', NULL )";
                 */
                setMessageString = "insert into chat (username,nickname,usermessage,datetime) VALUES('" + username.replace("'", "''") + "','"
                        + message.getNick().replace("'", "''") + "','" + message.getMessage().replace("'", "''") + "','"
                        + message.dateAsInt() + "')";
            }
        } else {
            if (message.getLocation() != null) {
                setMessageString = "insert into chat (username,nickname,usermessage,datetime,channel,location,temperature) VALUES('" + username.replace("'", "''") + "','"
                        + message.getNick().replace("'", "''") + "','" + message.getMessage().replace("'", "''") + "','"
                        + message.dateAsInt() + "','" + channel.replace("'", "''") + "','"
                        + message.getLocation().replace("'", "''") + "','" + message.getTemperature().replace("'", "''")
                        + "')";
            } else {
                setMessageString = "insert into chat (username,nickname,usermessage,datetime,channel) VALUES('" + username.replace("'", "''") + "','"
                        + message.getNick().replace("'", "''") + "','" + message.getMessage().replace("'", "''") + "','"
                        + message.dateAsInt() + "','" + channel.replace("'", "''") + "')";
            }
        }
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(setMessageString);
            statusMessage = "Message processed";
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while adding message to database");
            code = 400;
            statusMessage = "An error occurred while processing the message";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * editMessage method edits a previously saved message in the database
     */
    public List<String> editMessage(ChatMessage message, int messageid, String channel, String username) throws SQLException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        String editMessageString;
        List<String> oldmessage = checkMessage(messageid);
        // added these statements as otherwise vscode complains about duplication of the
        // strings in them
        String updatestatement = "UPDATE chat SET nickname = '";
        String wherestatement = "' WHERE id = '";
        if (channel != null && !channelExists(channel)) {
            code = 400;
            statusMessage = "channel (" + channel + ") doesn't exist";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (!oldmessage.get(0).equals(username)) {
            code = 403;
            statusMessage = "User cannot edit other users messages";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (oldmessage.get(1).equals("<deleted>")) {
            code = 403;
            statusMessage = "Messages that have been deleted cannot be edited.";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (channel == null && oldmessage.get(2) != null) {
            code = 400;
            statusMessage = "Message (" + messageid + ") was not found. It might be located on a channel";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (channel != null && !oldmessage.get(2).equals(channel)) {
            code = 400;
            statusMessage = "There is no message (" + messageid + ") on channel: " + channel;
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
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
            statusMessage = "Message was edited";
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while editing a message in the database");
            code = 400;
            statusMessage = "An error occurred while trying to edit the message";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }

    /*
     * deleteMessage method deletes a previously saved message
     */
    public List<String> deleteMessage(ChatMessage message, int messageid, String channel, String username) throws SQLException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        String editMessageString;
        List<String> oldmessage = checkMessage(messageid);
        if (channel != null && !channelExists(channel)) {
            code = 400;
            statusMessage = "channel (" + channel + ") doesn't exist";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (!oldmessage.get(0).equals(username) && !isAdmin(username)) {
            // normal users can only delete their own messages
            // admins can delete other users messages
            code = 403;
            statusMessage = "User cannot delete other users messages";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (channel == null && oldmessage.get(2) != null) {
            code = 400;
            statusMessage = "Message (" + messageid + ") was not found. It might be located on a channel";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (channel != null && !oldmessage.get(2).equals(channel)) {
            code = 400;
            statusMessage = "There is no message (" + messageid + ") on channel: " + channel;
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else {
            // Changing the message to <deleted> and setting location and temperature to null leaving only data on who and when
            editMessageString = "UPDATE chat SET nickname = '" + message.getNick().replace("'", "''")
                    + "', usermessage = '<deleted>', datetime = '" + message.dateAsInt() + "', location = NULL, temperature = NULL WHERE id = '" + messageid
                    + "'";
        }
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(editMessageString);
            statusMessage = "Message was deleted";
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while deleting a message from the database");
            code = 400;
            statusMessage = "An error occurred while trying to delete the message";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
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
                getMessagesString = "select id, nickname, usermessage, datetime, location, temperature from (select id, nickname, usermessage, datetime, location, temperature from chat where channel IS NULL order by datetime DESC limit 100) order by datetime ASC";
            } else {
                getMessagesString = "select id, nickname, usermessage, datetime, location, temperature from chat where channel IS NULL AND datetime > "
                        + since + " order by datetime ASC";
            }
        } else {
            if (since == -1) {
                getMessagesString = "select id, nickname, usermessage, datetime, location, temperature from (select id, nickname, usermessage, datetime, location, temperature from chat where channel = '"
                        + channel.replace("'", "''") + "' order by datetime DESC limit 100) order by datetime ASC";
            } else {
                getMessagesString = "select id, nickname, usermessage, datetime, location, temperature from chat where channel = '"
                        + channel.replace("'", "''") + "' AND datetime > " + since + " order by datetime ASC";
            }
        }
        String nickname = null;
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
                nickname = rs.getString("nickname");
                // Here we add the message id number to the start of the message
                message = "(" + rs.getInt("id") + ") " + rs.getString("usermessage");
                sent = null;
                location = rs.getString("location");
                temperature = rs.getString("temperature");
                ChatMessage chatmessage = new ChatMessage(sent, nickname, message, location, temperature);
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
        String getMessagesString = "select username, usermessage, channel from chat where id = " + messageid;
        String username = null;
        String message = null;
        String channel = null;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(getMessagesString);

            while (rs.next()) {
                username = rs.getString("username");
                // Here we form the message string from the message id number and the message
                // string
                message = rs.getString("usermessage");
                // message = "(" + rs.getInt("id") + ") " + rs.getString("usermessage");
                channel = rs.getString("channel");
            }

        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while searching a message from the database");
        }
        messageInfo.add(0, username);
        messageInfo.add(1, message);
        messageInfo.add(2, channel);
        return messageInfo;

    }

    /*
     * updateMessages method updates an users new username into old messages so they can still be recognized as that users messages.
     */
    public boolean updateMessages( String oldusername, String newusername) throws SQLException {
        boolean success = false;
        String editMessageString;
        editMessageString = "UPDATE chat SET username = '" + newusername.replace("'", "''") + "' WHERE username = '" + oldusername.replace("'", "''") + "'";
        
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(editMessageString);
            ChatServer.log("Messages were updated");
            success = true;
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while updating the messages in the database");
        }
        return success;
    }

    /*
     * createChannel method creates a new chat channel
     */
    public List<String> createChannel(String channel) throws SQLException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";

        if (!channelExists(channel)) {
            String setChannel = "insert into channel VALUES('" + channel.replace("'", "''") + "')";
            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(setChannel);
                statusMessage = "Channel " + channel + " successfully created";
                status.add(0, String.valueOf(code));
                status.add(1, statusMessage); 
                return status;
            } catch (SQLException e) {
                ChatServer.log("ERROR: SQLException while creating the channel");
                code = 400;
                statusMessage = "An error occurred while creating the channel";
            }
        } else {
            code = 400;
            statusMessage = "Channel with name " + channel + " already exists";
        }
        ChatServer.log("Channel creation failed");
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
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
