package com.mailbridge.models;

/**
 * Representa um destinatário lido de um ficheiro CSV ou Excel.
 *
 * Não é persistido na base de dados — existe apenas em memória
 * durante o processamento de um envio. Esta decisão simplifica
 * o schema e torna as campanhas independentes de listas guardadas.
 *
 * Campos suportados no ficheiro:
 *   - email (obrigatório)
 *   - name  (opcional — se em falta, usa a parte local do email)
 *   - Qualquer coluna extra é ignorada
 */
public class Recipient {
    private String name;
    private String email;

    public Recipient(String name, String email) {
        this.name  = name;
        this.email = email;
    }

    public String getName()  { return name; }
    public String getEmail() { return email; }
}
