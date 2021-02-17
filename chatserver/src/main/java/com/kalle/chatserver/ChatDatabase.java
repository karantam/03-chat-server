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

public class ChatDatabase {

    private Connection dbConnection = null;
    private static ChatDatabase singleton = null;
    private SecureRandom secureRandom = new SecureRandom();

    public static synchronized ChatDatabase getInstance(String dbName) {
        if (null == singleton) {
            singleton = new ChatDatabase(dbName);
        }
        return singleton;
    }

    private ChatDatabase(String dbName) {

        try {
            open(dbName);
        } catch (SQLException e) {
            ChatServer.log("ERROR SQLException");
        }
    }

    public void open(String dbName) throws SQLException {
        File dbFile = new File(dbName);
        boolean exists = dbFile.exists();
        // String currentDirectory = System.getProperty("user.dir");
        // String path = currentDirectory + "/" + dbName;
        // Changed the method of getting path to the database so there are no hardcoded
        // parts
        String path = dbFile.getAbsolutePath();
        String database = "jdbc:sqlite:" + path;
        dbConnection = DriverManager.getConnection(database);
        if (!exists) {
            initializeDatabase();
        }
    }

    private boolean initializeDatabase() throws SQLException {
        if (null != dbConnection) {
            String registrationDB = "create table registration (user varchar(50) PRIMARY KEY, userpassword varchar(50) NOT NULL, usersalt varchar(50) NOT NULL, useremail varchar(50) NOT NULL)";
            String chatDB = "create table chat (user varchar(50) NOT NULL, usermessage varchar(500) NOT NULL, datetime numeric(50) NOT NULL)";
            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(registrationDB);
                createStatement.executeUpdate(chatDB);
                ChatServer.log("DB successfully created");

                return true;
            } catch (SQLException e) {
                ChatServer.log("ERROR: SQLException while creating the database");
            }
        }

        ChatServer.log("DB creation failed");
        return false;
    }

    public void closeDB() throws SQLException {
        if (null != dbConnection) {
            dbConnection.close();
            ChatServer.log("closing db connection");
            dbConnection = null;
        }
    }

    public boolean setUser(User user) throws SQLException {
        // Registering a new user
        User existing = getUser(user.getUsername());
        // Cheking if the user name is already in use
        if (existing.getUsername() != null && existing.getUsername().equals(user.getUsername())) {
            ChatServer.log("ERROR: Invalid User");
            return false;
        } else {
            // Securing the password before saving it
            byte[] bytes = new byte[13];
            secureRandom.nextBytes(bytes);
            String saltBytes = new String(Base64.getEncoder().encode(bytes));
            String salt = "$6$" + saltBytes;
            String hashedPassword = Crypt.crypt(user.getPassword(), salt);
            // Saving the data of the new user into the database
            String setMessageString = "insert into registration " + "VALUES('" + user.getUsername() + "','"
                    + hashedPassword + "','" + salt + "','" + user.getEmail() + "')";
            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(setMessageString);
            } catch (SQLException e) {
                ChatServer.log("ERROR: SQLException while adding user to database");
            }
            return true;
        }
    }

    public User getUser(String user) throws SQLException {
        String getMessagesString = "select user, userpassword, useremail from registration where user =  \"" + user
                + "\"";
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

    public boolean setMessage(ChatMessage message) throws SQLException {
        String setMessageString = "insert into chat " + "VALUES('" + message.getNick() + "','" + message.getMessage()
                + "','" + message.dateAsInt() + "')";
        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(setMessageString);
            return true;
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while adding message to database");
        }
        return false;
    }

    public List<ChatMessage> getMessages(long since) throws SQLException {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        // Getting all the messages sent after variable since and sorting them by the
        // sending time in ascending order if since is -1 getting the last 100 messages
        String getMessagesString;
        if (since == -1) {
            getMessagesString = "select user, usermessage, datetime from (select user, usermessage, datetime from chat order by datetime DESC limit 100) order by datetime ASC";
        } else {
            getMessagesString = "select user, usermessage, datetime from chat where datetime > " + since
                    + " order by datetime ASC";
        }
        String username = null;
        String message = null;
        LocalDateTime sent = null;
        try (Statement queryStatement = dbConnection.createStatement()) {
            ResultSet rs = queryStatement.executeQuery(getMessagesString);

            while (rs.next()) {
                username = rs.getString("user");
                message = rs.getString("usermessage");
                sent = null;
                ChatMessage chatmessage = new ChatMessage(sent, username, message);
                chatmessage.setSent(rs.getLong("datetime"));
                messages.add(chatmessage);
            }

        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while reading messages from database");
        }
        return messages;

    }
}
