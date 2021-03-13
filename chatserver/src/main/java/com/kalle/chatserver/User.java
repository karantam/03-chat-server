package com.kalle.chatserver;

/*
 * User class creates a user object containing all the information about that user
 */
public class User {

    private String username;
    private String password;
    private String email;

    // Constructor for the User object
    public User(String usernamepar, String passwordpar, String emailpar) {
        this.username = usernamepar;
        this.password = passwordpar;
        this.email = emailpar;
    }

    // Get methods for the User object
    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

    public String getEmail() {
        return this.email;
    }

    public User getUser() {
        return new User(username, password, email);
    }
}
