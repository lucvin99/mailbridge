package com.mailbridge.controllers;

import com.google.gson.Gson;
import com.mailbridge.config.SessionManager;
import com.mailbridge.dto.DTOs;
import com.mailbridge.exceptions.ValidationException;
import com.mailbridge.models.User;
import com.mailbridge.services.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import static spark.Spark.*;

/**
 * Controller de autenticação — expõe os endpoints de registo e login.
 *
 * Endpoints:
 *   POST /api/auth/register — cria conta nova
 *   POST /api/auth/login    — autentica e devolve token
 *   POST /api/auth/logout   — invalida a sessão
 *
 * O token devolvido é guardado pelo browser em localStorage
 * e enviado no header Authorization em cada pedido subsequente.
 */
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    public static void init(Connection connection) {
        Gson gson = new Gson();
        AuthService service = new AuthService(connection);

        post("/api/auth/register", (req, res) -> {
            res.type("application/json");
            try {
                DTOs.RegisterRequest body = gson.fromJson(req.body(), DTOs.RegisterRequest.class);
                User user = service.register(body.name, body.email, body.password);
                String token = SessionManager.createSession(user);
                res.status(201);
                return gson.toJson(new DTOs.AuthResponse(token, user.getName(), user.getEmail()));
            } catch (ValidationException e) {
                res.status(400); return gson.toJson(new DTOs.ErrorResponse(e.getMessage()));
            } catch (Exception e) {
                logger.error("Erro no registo", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno: " + e.getMessage()));
            }
        });

        post("/api/auth/login", (req, res) -> {
            res.type("application/json");
            try {
                DTOs.LoginRequest body = gson.fromJson(req.body(), DTOs.LoginRequest.class);
                User user = service.login(body.email, body.password);
                String token = SessionManager.createSession(user);
                return gson.toJson(new DTOs.AuthResponse(token, user.getName(), user.getEmail()));
            } catch (ValidationException e) {
                res.status(401); return gson.toJson(new DTOs.ErrorResponse(e.getMessage()));
            } catch (Exception e) {
                logger.error("Erro no login", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno: " + e.getMessage()));
            }
        });

        post("/api/auth/logout", (req, res) -> {
            res.type("application/json");
            String token = extractToken(req.headers("Authorization"));
            if (token != null) SessionManager.invalidate(token);
            return "{\"message\":\"Sessão terminada\"}";
        });
    }

    /** Extrai o token do header Authorization: Bearer <token> */
    static String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7);
    }

    /** Autentica o pedido e devolve o utilizador, ou null se não autenticado */
    public static User authenticate(String authHeader) {
        String token = extractToken(authHeader);
        return SessionManager.getUser(token);
    }
}
