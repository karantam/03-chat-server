package com.kalle.chatserver;

import java.util.Hashtable;
import java.util.Map;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {

    private Map<String, User> users = null;

    public ChatAuthenticator() {
        super("chat");
        users = new Hashtable<>();
        User user = new User("dummy", "passwd", "email");
        users.put(user.getUsername(), user);
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        String pass = users.get(username).getPassword();
        Boolean value = false;
        // Cheking if user exists and his password is correct
        if (users.containsKey(username) && pass.equals(password)) {
            value = true;
        }
        return value;
    }

    public boolean addUser(User user) {
        // adding new users
        if (users.containsKey(user.getUsername())) {
            return false;
        } else {
            users.put(user.getUsername(), user);
            return true;
        }
    }
}
