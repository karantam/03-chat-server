package com.kalle.chatserver;

public class User {

    private String username;
    private String password;
    private String email;

    public User(String usernamepar, String passwordpar, String emailpar) {
        this.username = usernamepar;
        this.password = passwordpar;
        this.email = emailpar;
    }

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
