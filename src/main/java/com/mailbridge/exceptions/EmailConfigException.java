package com.mailbridge.exceptions;

/**
 * Lançada quando o serviço de email não está correctamente configurado.
 * Acontece quando mail.from ou mail.password estão em branco no db.properties.
 * Resulta num HTTP 400 com mensagem explicativa para o utilizador.
 */
public class EmailConfigException extends RuntimeException {
    public EmailConfigException(String message) { super(message); }
}
