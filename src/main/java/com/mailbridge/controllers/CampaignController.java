package com.mailbridge.controllers;

import com.google.gson.Gson;
import com.mailbridge.dto.DTOs;
import com.mailbridge.exceptions.NotFoundException;
import com.mailbridge.exceptions.ValidationException;
import com.mailbridge.models.User;
import com.mailbridge.services.CampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import static spark.Spark.*;

/**
 * Controller de campanhas.
 *
 * Endpoints:
 *   POST   /api/campaigns      — cria campanha
 *   GET    /api/campaigns      — lista campanhas do utilizador
 *   DELETE /api/campaigns/:id  — elimina campanha
 *
 * Todos os endpoints requerem autenticação via header Authorization.
 */
public class CampaignController {
    private static final Logger logger = LoggerFactory.getLogger(CampaignController.class);

    public static void init(Connection connection) {
        Gson gson = new Gson();
        CampaignService service = new CampaignService(connection);

        post("/api/campaigns", (req, res) -> {
            res.type("application/json");
            try {
                User user = AuthController.authenticate(req.headers("Authorization"));
                if (user == null) { res.status(401); return gson.toJson(new DTOs.ErrorResponse("Não autenticado")); }

                DTOs.CampaignRequest body = gson.fromJson(req.body(), DTOs.CampaignRequest.class);
                service.create(body, user.getId());
                res.status(201);
                return "{\"message\":\"Campanha criada\"}";
            } catch (ValidationException e) {
                res.status(400); return gson.toJson(new DTOs.ErrorResponse(e.getMessage()));
            } catch (Exception e) {
                logger.error("Erro ao criar campanha", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno"));
            }
        });

        get("/api/campaigns", (req, res) -> {
            res.type("application/json");
            try {
                User user = AuthController.authenticate(req.headers("Authorization"));
                if (user == null) { res.status(401); return gson.toJson(new DTOs.ErrorResponse("Não autenticado")); }
                return gson.toJson(service.getAll(user.getId()));
            } catch (Exception e) {
                logger.error("Erro ao listar campanhas", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno"));
            }
        });

        delete("/api/campaigns/:id", (req, res) -> {
            res.type("application/json");
            try {
                User user = AuthController.authenticate(req.headers("Authorization"));
                if (user == null) { res.status(401); return gson.toJson(new DTOs.ErrorResponse("Não autenticado")); }
                service.delete(Integer.parseInt(req.params(":id")), user.getId());
                return "{\"message\":\"Eliminada\"}";
            } catch (NotFoundException e) {
                res.status(404); return gson.toJson(new DTOs.ErrorResponse(e.getMessage()));
            } catch (Exception e) {
                logger.error("Erro ao eliminar campanha", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno"));
            }
        });
    }
}
