-- ============================================================
-- MailBridge — Schema da Base de Dados
-- Corre este ficheiro no HeidiSQL antes de iniciar o servidor
-- ============================================================

CREATE DATABASE IF NOT EXISTS mailbridge;
USE mailbridge;

-- Tabela de utilizadores
-- Cada utilizador tem as suas próprias campanhas e histórico isolados
CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,              -- Password encriptada com BCrypt
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de campanhas
-- Uma campanha define o assunto, o template e a lista de destinatários
CREATE TABLE IF NOT EXISTS campaigns (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT NOT NULL,
    name       VARCHAR(100) NOT NULL,                 -- Nome interno para identificar a campanha
    subject    VARCHAR(200) NOT NULL,                 -- Assunto do email (suporta {{name}})
    message    TEXT         NOT NULL,                 -- Corpo do email (suporta {{name}}, {{email}})
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Tabela de envios (deliveries)
-- Regista cada vez que uma campanha é enviada, com o progresso em tempo real
CREATE TABLE IF NOT EXISTS deliveries (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT NOT NULL,
    campaign_id    INT NOT NULL,
    total_contacts INT NOT NULL DEFAULT 0,            -- Total de destinatários na lista
    sent           INT NOT NULL DEFAULT 0,            -- Emails enviados com sucesso
    failed         INT NOT NULL DEFAULT 0,            -- Emails que falharam
    status         VARCHAR(20) NOT NULL DEFAULT 'pending', -- pending | running | done | error
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)     REFERENCES users(id)     ON DELETE CASCADE,
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE
);
