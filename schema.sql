DROP DATABASE IF EXISTS mailbridge;

CREATE DATABASE mailbridge;

USE mailbridge;

-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,

    name VARCHAR(100) NOT NULL,

    email VARCHAR(150) NOT NULL UNIQUE,

    password_hash VARCHAR(255) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- CONTACTS
-- ============================================================

CREATE TABLE contacts (
    id INT AUTO_INCREMENT PRIMARY KEY,

    user_id INT NOT NULL,

    name VARCHAR(100) NOT NULL,

    email VARCHAR(150) NOT NULL,

    company VARCHAR(100),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE
);

-- ============================================================
-- CAMPAIGNS
-- ============================================================

CREATE TABLE campaigns (
    id INT AUTO_INCREMENT PRIMARY KEY,

    user_id INT NOT NULL,

    name VARCHAR(100) NOT NULL,

    subject VARCHAR(200) NOT NULL,

    message TEXT NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE
);

-- ============================================================
-- DELIVERIES
-- ============================================================

CREATE TABLE deliveries (
    id INT AUTO_INCREMENT PRIMARY KEY,

    user_id INT NOT NULL,

    campaign_id INT NOT NULL,

    total_contacts INT NOT NULL DEFAULT 0,

    sent INT NOT NULL DEFAULT 0,

    failed INT NOT NULL DEFAULT 0,

    status VARCHAR(20) NOT NULL DEFAULT 'pending',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE,

    FOREIGN KEY (campaign_id)
    REFERENCES campaigns(id)
    ON DELETE CASCADE
);