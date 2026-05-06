package com.mailbridge.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO (Data Transfer Object) — objectos usados para transportar dados
 * entre o frontend (HTTP) e a camada de serviços.
 *
 * Porquê usar DTOs em vez dos modelos directamente?
 *   - Separam o que o utilizador envia do que a base de dados guarda
 *   - Evitam expor campos internos (ex: passwordHash, userId)
 *   - Permitem validar antes de criar o modelo de domínio
 */
public class DTOs {

    /** Dados necessários para criar uma conta */
    public static class RegisterRequest {
        public String name;
        public String email;
        public String password;
    }

    /** Dados necessários para fazer login */
    public static class LoginRequest {
        public String email;
        public String password;
    }

    /** Resposta após login/registo bem-sucedido */
    public static class AuthResponse {
        public String token;
        public String name;
        public String email;

        public AuthResponse(String token, String name, String email) {
            this.token = token; this.name = name; this.email = email;
        }
    }

    /** Dados para criar uma campanha */
    public static class CampaignRequest {
        public String name;
        public String subject;
        public String message;
    }

    /** Pedido de início de envio — liga uma campanha a um ficheiro já carregado */
    public static class DeliveryRequest {
        public int    campaignId;
        public String fileKey;
    }

    /** Estatísticas do dashboard */
    public static class StatsResponse {
        public int totalCampaigns;
        public int totalDeliveries;
        public int totalEmailsSent;
        public int totalEmailsFailed;

        public StatsResponse(int totalCampaigns, int totalDeliveries,
                             int totalEmailsSent, int totalEmailsFailed) {
            this.totalCampaigns    = totalCampaigns;
            this.totalDeliveries   = totalDeliveries;
            this.totalEmailsSent   = totalEmailsSent;
            this.totalEmailsFailed = totalEmailsFailed;
        }
    }

    /**
     * Resposta após importação de ficheiro.
     * Inclui variáveis detectadas e primeiro destinatário para pré-visualização.
     */
    public static class ImportResponse {
        public String       fileKey;         // chave para usar no envio
        public int          count;           // total de destinatários válidos
        public String       preview;         // primeiros emails resumidos
        public List<String> vars;            // variáveis dinâmicas detectadas (colunas extra)
        public FirstRecipient firstRecipient; // primeiro destinatário para pré-visualização

        public ImportResponse(String fileKey, int count, String preview,
                              List<String> vars, FirstRecipient firstRecipient) {
            this.fileKey        = fileKey;
            this.count          = count;
            this.preview        = preview;
            this.vars           = vars;
            this.firstRecipient = firstRecipient;
        }
    }

    /**
     * Dados do primeiro destinatário usados para pré-visualização do email
     * antes de confirmar o envio.
     */
    public static class FirstRecipient {
        public String name;
        public String email;
        public Map<String, String> fields;

        public FirstRecipient(String name, String email, Map<String, String> fields) {
            this.name   = name;
            this.email  = email;
            this.fields = fields;
        }
    }

    /** Resposta genérica de erro */
    public static class ErrorResponse {
        public String error;
        public ErrorResponse(String error) { this.error = error; }
    }
}