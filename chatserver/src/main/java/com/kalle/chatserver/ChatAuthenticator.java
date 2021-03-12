package com.kalle.chatserver;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.BasicAuthenticator;

/*
 * ChatAuthenticator class authenticates the user
 */
public class ChatAuthenticator extends BasicAuthenticator {

    private static final int MINNAME = 2;
    private static final int MINPASS = 5;

    public ChatAuthenticator() {
        super("chat");
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        Boolean value = false;
        try {
            value = ChatDatabase.getInstance().checkUser(username, password);
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while authenticating user");
        }
        return value;
    }

    /*
     * addUser method adds a new valid user
     */
    public List<String> addUser(User user) throws SQLException {
        List<String> status = new ArrayList<>(2);
        int code = 200;
        String statusMessage = "";
        Boolean add = false;
        // adding new users
        if (user.getUsername().length() < MINNAME) {
            code = 400;
            statusMessage = "Name must be at least three characters long";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;
        } else if (user.getPassword().length() < MINPASS) {
            code = 400;
            statusMessage = "Password must be at least five characters long";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;

        } else if (!user.getEmail().contains("@")) {
            code = 400;
            statusMessage = "Invalid email address";
            status.add(0, String.valueOf(code));
            status.add(1, statusMessage);
            return status;

        }
        try {
            add = ChatDatabase.getInstance().setUser(user);
            if (Boolean.TRUE.equals(add)) {
                statusMessage = "New user has been registered";
            } else {
                code = 403;
                statusMessage = "Invalid user credentials";
            }
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while adding user");
            code = 400;
            statusMessage = "An error occurred while registering user";
        }
        status.add(0, String.valueOf(code));
        status.add(1, statusMessage);
        return status;
    }
}
