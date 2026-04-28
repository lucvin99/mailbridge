package com.mailbridge.dto;

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
        public String fileKey;  // chave temporária do ficheiro carregado em memória
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

    /** Resposta após importação de ficheiro */
    public static class ImportResponse {
        public String fileKey;     // identificador temporário para usar no envio
        public int    count;       // número de destinatários válidos encontrados
        public String preview;     // primeiros emails para o utilizador confirmar

        public ImportResponse(String fileKey, int count, String preview) {
            this.fileKey = fileKey; this.count = count; this.preview = preview;
        }
    }

    /** Resposta genérica de erro */
    public static class ErrorResponse {
        public String error;
        public ErrorResponse(String error) { this.error = error; }
    }
}
