package com.mailbridge.models;

/**
 * Representa um utilizador registado na plataforma.
 * Cada utilizador tem as suas campanhas e histórico isolados dos outros.
 */
public class User {
    private int    id;
    private String name;
    private String email;
    private String passwordHash; // Nunca exposto na API — só usado internamente
    private String createdAt;

    public User() {}
    public User(int id, String name, String email, String passwordHash, String createdAt) {
        this.id = id; this.name = name; this.email = email;
        this.passwordHash = passwordHash; this.createdAt = createdAt;
    }

    public int    getId()           { return id; }
    public String getName()         { return name; }
    public String getEmail()        { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getCreatedAt()    { return createdAt; }
    public void setId(int id)                       { this.id = id; }
    public void setName(String name)               { this.name = name; }
    public void setEmail(String email)             { this.email = email; }
    public void setPasswordHash(String h)          { this.passwordHash = h; }
    public void setCreatedAt(String createdAt)     { this.createdAt = createdAt; }
}
