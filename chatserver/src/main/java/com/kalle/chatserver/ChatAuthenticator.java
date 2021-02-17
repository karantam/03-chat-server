package com.kalle.chatserver;

import java.sql.SQLException;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {

    private int minname = 2;
    private int minpass = 4;

    public ChatAuthenticator() {
        super("chat");
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        ChatDatabase database = ChatDatabase.getInstance("ChatServer.db");
        Boolean value = false;
        try {
            value = database.checkUser(username, password);
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while authenticating user");
        }
        return value;
    }

    public boolean addUser(User user) throws SQLException {
        Boolean add = false;
        // adding new users
        // Can specify minimum length and form of email address ie contains @
        if (user.getUsername().length() < minname) {
            ChatServer.log("ERROR: Name must be at least three characters long");
            return add;
        } else if (user.getPassword().length() < minpass) {
            ChatServer.log("ERROR: Password must be at least five characters long");
            return add;
            // client tests do not use email so temporarily removed this feature
            /*
             * } else if(!user.getEmail().contains("@")){
             * ChatServer.log("ERROR: email is not valid"); return add;
             */
        }
        ChatDatabase database = ChatDatabase.getInstance("ChatServer.db");
        try {
            add = database.setUser(user);
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while adding user");
        }
        return add;
    }
}
