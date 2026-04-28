package com.mailbridge.exceptions;

/**
 * Lançada quando os dados fornecidos pelo utilizador não passam na validação.
 * Exemplos: email inválido, campos em branco, formato incorrecto.
 * Resulta sempre num HTTP 400 (Bad Request) na resposta da API.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}
