package com.mailbridge.config;

import com.mailbridge.controllers.AuthController;
import com.mailbridge.controllers.CampaignController;
import com.mailbridge.controllers.DeliveryController;
import com.mailbridge.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import static spark.Spark.*;

/**
 * Configuração central do servidor.
 *
 * Responsabilidades:
 *   - Iniciar o servidor Spark na porta 4567
 *   - Servir os ficheiros estáticos HTML/CSS/JS
 *   - Configurar CORS para permitir pedidos do browser
 *   - Registar todos os controllers (rotas da API)
 *   - Estabelecer a ligação à base de dados
 *
 * Padrão de arquitectura: todos os controllers recebem a mesma
 * Connection, que é partilhada por toda a aplicação (Singleton).
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    public static void startServer() {
        port(4567);

        // Serve os ficheiros em src/main/resources/static/ directamente na raiz
        staticFileLocation("/static");

        // CORS — permite que o browser faça pedidos à API a partir de qualquer origem
        // Necessário porque o browser bloqueia pedidos cross-origin por defeito
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin",  "*");
            res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        // Responde a pedidos OPTIONS (preflight do CORS) sem chegar aos controllers
        options("/*", (req, res) -> { res.status(200); return "OK"; });

        // Estabelece ligação à BD — partilhada por todos os controllers
        Connection connection = DatabaseConnection.getConnection();

        // Regista os controllers — cada um define as suas rotas
        AuthController.init(connection);
        CampaignController.init(connection);
        DeliveryController.init(connection);

        // Endpoint de saúde — útil para verificar se o servidor está activo
        get("/api/health", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"ok\",\"app\":\"MailBridge\"}";
        });

        logger.info("======================================");
        logger.info("  MailBridge a correr em :4567");
        logger.info("  http://localhost:4567");
        logger.info("======================================");
    }
}
