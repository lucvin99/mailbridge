package com.mailbridge.models;

/**
 * Representa uma campanha de email.
 *
 * Uma campanha define:
 *   - name:    nome interno para identificação
 *   - subject: assunto do email (pode conter {{name}})
 *   - message: corpo do email (pode conter {{name}} e {{email}})
 *
 * A lista de destinatários NÃO é guardada na campanha —
 * é carregada via CSV/Excel no momento do envio (delivery).
 * Isto mantém as campanhas reutilizáveis para diferentes listas.
 */
public class Campaign {
    private int    id;
    private int    userId;
    private String name;
    private String subject;
    private String message;
    private String createdAt;

    public Campaign() {}
    public Campaign(int id, int userId, String name, String subject, String message, String createdAt) {
        this.id = id; this.userId = userId; this.name = name;
        this.subject = subject; this.message = message; this.createdAt = createdAt;
    }

    public int    getId()        { return id; }
    public int    getUserId()    { return userId; }
    public String getName()      { return name; }
    public String getSubject()   { return subject; }
    public String getMessage()   { return message; }
    public String getCreatedAt() { return createdAt; }
    public void setId(int id)              { this.id = id; }
    public void setUserId(int userId)      { this.userId = userId; }
    public void setName(String name)       { this.name = name; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setMessage(String message) { this.message = message; }
    public void setCreatedAt(String c)     { this.createdAt = c; }
}
