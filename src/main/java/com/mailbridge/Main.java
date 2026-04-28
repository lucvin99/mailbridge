package com.mailbridge;

import com.mailbridge.config.AppConfig;

/**
 * Ponto de entrada da aplicação.
 * Responsabilidade única: iniciar o servidor.
 * Toda a configuração de rotas e dependências está em AppConfig.
 */
public class Main {
    public static void main(String[] args) {
        AppConfig.startServer();
    }
}
