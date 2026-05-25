package com.sigeu.api.dto;

import com.sigeu.api.model.User;

public class AuthResponse {
    private Long id;
    private String username;
    private String role;
    private String name;
    private String token;

    public AuthResponse(User user, String token) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.role = user.getRole();
        this.name = user.getName();
        this.token = token;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getName() { return name; }
    public String getToken() { return token; }
}
