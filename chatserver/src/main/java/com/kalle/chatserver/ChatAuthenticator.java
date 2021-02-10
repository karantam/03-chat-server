package com.kalle.chatserver;

import java.sql.SQLException;
//import java.util.Hashtable;
//import java.util.Map;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {

    //private Map<String, User> users = null;
    private int minname = 2;
    private int minpass = 4;

    public ChatAuthenticator() {
        super("chat");
        //users = new Hashtable<>();
        //User user = new User("dummy", "passwd", "email");
        //users.put(user.getUsername(), user);
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
        /*String pass = users.get(username).getPassword();
        Boolean value = false;
        // Cheking if user exists and his password is correct
        if (users.containsKey(username) && pass.equals(password)) {
            value = true;
        }*/
        return value;
    }

    public boolean addUser(User user) throws SQLException {
        Boolean add = false;
        // adding new users
        // Can specify minimum length and form of email address ie contains @
        if (user.getUsername().length() < minname){
            ChatServer.log("ERROR: Name must be at least three characters long");
            return add;
        } else if(user.getPassword().length() < minpass){
            ChatServer.log("ERROR: Password must be at least five characters long");
            return add;
        } else if(!user.getEmail().contains("@")){
            ChatServer.log("ERROR: email is not valid");
            return add;
        }
        ChatDatabase database = ChatDatabase.getInstance("ChatServer.db");
        try {
            add = database.setUser(user);
        } catch (SQLException e) {
            ChatServer.log("ERROR: SQLException while adding user");
        }
        return add;
        /*if (users.containsKey(user.getUsername())) {
            return false;
        } else {
            users.put(user.getUsername(), user);
            return true;
        }*/
        
    }
}
