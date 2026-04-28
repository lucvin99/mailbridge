package com.mailbridge.controllers;

import com.google.gson.Gson;
import com.mailbridge.dto.DTOs;
import com.mailbridge.exceptions.EmailConfigException;
import com.mailbridge.exceptions.NotFoundException;
import com.mailbridge.exceptions.ValidationException;
import com.mailbridge.models.Recipient;
import com.mailbridge.models.User;
import com.mailbridge.services.DeliveryService;
import com.mailbridge.services.FileParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;

import static spark.Spark.*;

/**
 * Controller de envios — o mais importante da aplicação.
 *
 * Endpoints:
 *   POST /api/deliveries/upload   — carrega ficheiro, devolve fileKey + preview
 *   POST /api/deliveries          — inicia envio (campanha + fileKey)
 *   GET  /api/deliveries          — lista histórico de envios
 *   GET  /api/deliveries/:id      — progresso de um envio específico (polling)
 *   GET  /api/stats               — estatísticas para o dashboard
 */
public class DeliveryController {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);

    public static void init(Connection connection) {
        Gson gson = new Gson();
        DeliveryService service = new DeliveryService(connection);

        /**
         * Upload do ficheiro de destinatários.
         * Devolve fileKey (para usar no envio) e preview dos primeiros emails.
         * O ficheiro NÃO é guardado na BD — fica em memória até ser usado.
         */
        post("/api/deliveries/upload", (req, res) -> {
            res.type("application/json");
            try {
                User user = AuthController.authenticate(req.headers("Authorization"));
                if (user == null) { res.status(401); return gson.toJson(new DTOs.ErrorResponse("Não autenticado")); }

                // Configura o multipart ANTES de qualquer leitura do request
                req.raw().setAttribute("org.eclipse.jetty.multipartConfig",
                        new MultipartConfigElement(System.getProperty("java.io.tmpdir"),
                                10 * 1024 * 1024, 20 * 1024 * 1024, 1024 * 1024));

                Part filePart = req.raw().getPart("file");
                if (filePart == null) {
                    res.status(400); return gson.toJson(new DTOs.ErrorResponse("Ficheiro não encontrado"));
                }

                String fileName = filePart.getSubmittedFileName();
                // Lê tudo para memória — evita conflito entre Jetty e Apache POI
                byte[] data = FileParserService.readAllBytes(filePart.getInputStream());

                logger.info("Upload recebido — '{}', {} bytes, user {}", fileName, data.length, user.getId());

                String fileKey = service.storeFile(data, fileName);
                List<Recipient> recipients = service.getRecipients(fileKey);

                // Guarda de volta (storeFile remove em startDelivery, não aqui)
                // Recria o fileKey para preview sem consumir
                String previewKey = service.storeFile(data, fileName);

                // Preview: primeiros 3 emails para o utilizador confirmar
                String preview = recipients.stream()
                        .limit(3)
                        .map(r -> r.getName() + " <" + r.getEmail() + ">")
                        .collect(Collectors.joining(", "));
                if (recipients.size() > 3) preview += " ... +" + (recipients.size() - 3) + " mais";

                return gson.toJson(new DTOs.ImportResponse(previewKey, recipients.size(), preview));

            } catch (ValidationException e) {
                res.status(400); return gson.toJson(new DTOs.ErrorResponse(e.getMessage()));
            } catch (Exception e) {
                logger.error("Erro no upload", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro ao processar ficheiro: " + e.getMessage()));
            }
        });

        /** Inicia o envio de uma campanha para a lista carregada */
        post("/api/deliveries", (req, res) -> {
            res.type("application/json");
            try {
                User user = AuthController.authenticate(req.headers("Authorization"));
                if (user == null) { res.status(401); return gson.toJson(new DTOs.ErrorResponse("Não autenticado")); }

                DTOs.DeliveryRequest body = gson.fromJson(req.body(), DTOs.DeliveryRequest.class);
                int deliveryId = service.startDelivery(body.campaignId, body.fileKey, user.getId());
                res.status(201);
                return "{\"message\":\"Envio iniciado\",\"deliveryId\":" + deliveryId + "}";

            } catch (ValidationException | NotFoundException e) {
                res.status(400); return gson.toJson(new DTOs.ErrorResponse(e.getMessage()));
            } catch (EmailConfigException e) {
                res.status(400); return gson.toJson(new DTOs.ErrorResponse(e.getMessage()));
            } catch (Exception e) {
                logger.error("Erro ao iniciar envio", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno"));
            }
        });

        /** Lista histórico de envios do utilizador */
        get("/api/deliveries", (req, res) -> {
            res.type("application/json");
            try {
                User user = AuthController.authenticate(req.headers("Authorization"));
                if (user == null) { res.status(401); return gson.toJson(new DTOs.ErrorResponse("Não autenticado")); }
                return gson.toJson(service.getAll(user.getId()));
            } catch (Exception e) {
                logger.error("Erro ao listar envios", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno"));
            }
        });

        /** Progresso de um envio — usado pelo frontend para polling em tempo real */
        get("/api/deliveries/:id", (req, res) -> {
            res.type("application/json");
            try {
                User user = AuthController.authenticate(req.headers("Authorization"));
                if (user == null) { res.status(401); return gson.toJson(new DTOs.ErrorResponse("Não autenticado")); }
                int id = Integer.parseInt(req.params(":id"));
                return gson.toJson(service.getById(id, user.getId()));
            } catch (NotFoundException e) {
                res.status(404); return gson.toJson(new DTOs.ErrorResponse(e.getMessage()));
            } catch (Exception e) {
                logger.error("Erro ao buscar envio", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno"));
            }
        });

        /** Estatísticas para o dashboard */
        get("/api/stats", (req, res) -> {
            res.type("application/json");
            try {
                User user = AuthController.authenticate(req.headers("Authorization"));
                if (user == null) { res.status(401); return gson.toJson(new DTOs.ErrorResponse("Não autenticado")); }
                int[] totals = service.getTotals(user.getId());
                return gson.toJson(new DTOs.StatsResponse(
                        service.count(user.getId()) > 0 ? service.count(user.getId()) : 0,
                        service.count(user.getId()),
                        totals[0], totals[1]
                ));
            } catch (Exception e) {
                logger.error("Erro ao carregar stats", e);
                res.status(500); return gson.toJson(new DTOs.ErrorResponse("Erro interno"));
            }
        });
    }
}
