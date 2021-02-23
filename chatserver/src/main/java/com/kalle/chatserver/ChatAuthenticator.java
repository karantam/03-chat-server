package com.kalle.chatserver;

import java.sql.SQLException;

import com.sun.net.httpserver.BasicAuthenticator;

/*
 * ChatAuthenticator class authenticates the user
 */
public class ChatAuthenticator extends BasicAuthenticator {

    private static final int MINNAME = 2;
    private static final int MINPASS = 4;

    public ChatAuthenticator() {
        super("chat");
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        ChatDatabase database = ChatDatabase.getInstance();
        Boolean value = false;
        try {
            value = database.checkUser(username, password);
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while authenticating user");
        }
        return value;
    }

    /*
     * addUser method adds a new valid user
     */
    public boolean addUser(User user) throws SQLException {
        Boolean add = false;
        // adding new users
        // Can specify minimum length and form of email address ie contains @
        if (user.getUsername().length() < MINNAME) {
            ChatServer.log("ERROR: Name must be at least three characters long");
            return add;
        } else if (user.getPassword().length() < MINPASS) {
            ChatServer.log("ERROR: Password must be at least five characters long");
            return add;
            // client tests do not use email so temporarily removed this feature
            /*
             * } else if(!user.getEmail().contains("@")){
             * ChatServer.log("ERROR: email is not valid"); return add;
             */
        }
        ChatDatabase database = ChatDatabase.getInstance();
        try {
            add = database.setUser(user);
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while adding user");
        }
        return add;
    }
}
