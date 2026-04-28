package com.mailbridge.exceptions;

/**
 * Lançada quando um recurso pedido não existe na base de dados.
 * Exemplos: campanha não encontrada, utilizador inexistente.
 * Resulta num HTTP 404 (Not Found) na resposta da API.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
