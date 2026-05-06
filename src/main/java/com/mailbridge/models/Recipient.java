package com.mailbridge.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Representa um destinatário lido de um ficheiro CSV ou Excel.
 *
 * Para além de name e email, guarda todas as colunas extra do ficheiro
 * num Map de campos dinâmicos. Isto permite usar qualquer coluna como
 * variável no template — ex: {{empresa}}, {{desconto}}, {{cidade}}.
 *
 * Não é persistido na base de dados — existe apenas em memória
 * durante o processamento de um envio.
 */
public class Recipient {
    private String name;
    private String email;
    // Campos extras lidos do ficheiro — qualquer coluna além de name e email
    private Map<String, String> fields;

    public Recipient(String name, String email, Map<String, String> fields) {
        this.name   = name;
        this.email  = email;
        this.fields = fields != null ? fields : new HashMap<>();
    }

    public String getName()  { return name; }
    public String getEmail() { return email; }

    /** Devolve todos os campos dinâmicos lidos do ficheiro */
    public Map<String, String> getFields() { return fields; }
}