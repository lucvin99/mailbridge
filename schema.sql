-- ============================================================
-- MailBridge — Schema da Base de Dados
-- Versão: 2.0
--
-- INSTRUÇÕES:
--   1. Abre o HeidiSQL
--   2. Liga à tua base de dados MariaDB
--   3. Cola este ficheiro e executa com F9
--
-- ATENÇÃO: Este script apaga e recria a base de dados completa.
-- ============================================================

DROP DATABASE IF EXISTS mailbridge;
CREATE DATABASE mailbridge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mailbridge;

-- ============================================================
-- USERS — Utilizadores registados na plataforma
-- Cada utilizador tem os seus próprios dados isolados
-- ============================================================
CREATE TABLE users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(150)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,          -- BCrypt hash, nunca texto simples
    avatar        MEDIUMTEXT    NULL,              -- imagem em base64 (opcional)
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- CAMPAIGNS — Templates de email criados pelo utilizador
-- O conteúdo pode ser texto simples ou HTML (do editor GrapesJS)
-- As variáveis {{name}}, {{email}}, etc. são substituídas no envio
-- ============================================================
CREATE TABLE campaigns (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT          NOT NULL,
    name       VARCHAR(100) NOT NULL,              -- nome interno
    subject    VARCHAR(200) NOT NULL,              -- assunto do email
    message    MEDIUMTEXT   NOT NULL,              -- corpo HTML ou texto
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ============================================================
-- DELIVERIES — Registo de cada envio realizado
-- Um delivery representa uma execução de uma campanha para uma lista
-- O status evolui: pending → running → done | error
-- ============================================================
CREATE TABLE deliveries (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT         NOT NULL,
    campaign_id    INT         NOT NULL,
    total_contacts INT         NOT NULL DEFAULT 0,
    sent           INT         NOT NULL DEFAULT 0,
    failed         INT         NOT NULL DEFAULT 0,
    status         VARCHAR(20) NOT NULL DEFAULT 'pending',
    scheduled_at   DATETIME    NULL,               -- NULL = envio imediato
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)     REFERENCES users(id)     ON DELETE CASCADE,
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE
);

-- ============================================================
-- DELIVERY_LOGS — Log detalhado por destinatário
-- Permite mostrar no histórico exactamente que emails falharam e porquê
-- ============================================================
CREATE TABLE delivery_logs (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    delivery_id  INT          NOT NULL,
    email        VARCHAR(150) NOT NULL,             -- email do destinatário
    status       VARCHAR(20)  NOT NULL,             -- 'sent' ou 'failed'
    error_type   VARCHAR(100) NULL,                 -- tipo de erro (ex: AuthenticationFailed)
    error_detail TEXT         NULL,                 -- mensagem completa do erro
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (delivery_id) REFERENCES deliveries(id) ON DELETE CASCADE
);