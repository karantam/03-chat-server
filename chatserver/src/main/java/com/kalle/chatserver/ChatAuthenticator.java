package com.kalle.chatserver;

import java.util.Hashtable;
import java.util.Map;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {

    private Map<String, String> users = null;

    public ChatAuthenticator() {
        super("chat");
        users = new Hashtable<String, String>();
        users.put("dummy", "passwd");
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        String pass = users.get(username);
        Boolean value = false;
        // Cheking if user exists and his password is correct
        if (users.containsKey(username) && pass.equals(password)) {
            value = true;
        }
        return value;
    }

    public boolean addUser(String userName, String password) {
        // adding new users
        if (users.containsKey(userName)) {
            return false;
        } else {
            users.put(userName, password);
            return true;
        }
    }
}
