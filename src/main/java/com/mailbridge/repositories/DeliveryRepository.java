package com.mailbridge.repositories;

import com.mailbridge.models.Delivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório de envios — gere as tabelas deliveries e delivery_logs.
 *
 * Padrão Repository: isola a lógica SQL do resto da aplicação.
 * Os services chamam métodos aqui sem saber como os dados são guardados.
 *
 * delivery_logs regista o resultado de cada email individual,
 * permitindo mostrar no histórico exactamente o que falhou e porquê.
 */
public class DeliveryRepository {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryRepository.class);
    private final Connection connection;

    public DeliveryRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Cria um novo registo de envio com status 'pending'.
     * Devolve o ID gerado para ser usado nas actualizações de progresso.
     *
     * @param scheduledAt null = envio imediato, data futura = agendado
     */
    public int create(int userId, int campaignId, int totalContacts, Timestamp scheduledAt) throws SQLException {
        String sql = "INSERT INTO deliveries (user_id, campaign_id, total_contacts, sent, failed, status, scheduled_at) " +
                     "VALUES (?, ?, ?, 0, 0, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, campaignId);
            stmt.setInt(3, totalContacts);
            stmt.setString(4, scheduledAt != null ? "scheduled" : "pending");
            stmt.setTimestamp(5, scheduledAt);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    logger.info("Delivery #{} criado — campanha {}, {} destinatários, agendado: {}",
                            id, campaignId, totalContacts, scheduledAt != null ? scheduledAt : "imediato");
                    return id;
                }
            }
        }
        return -1;
    }

    /** Actualiza o progresso de um envio em curso */
    public void updateProgress(int id, int sent, int failed, String status) throws SQLException {
        String sql = "UPDATE deliveries SET sent = ?, failed = ?, status = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, sent);
            stmt.setInt(2, failed);
            stmt.setString(3, status);
            stmt.setInt(4, id);
            stmt.executeUpdate();
        }
    }

    /**
     * Guarda o log de um email individual.
     * Chamado após cada tentativa de envio — sucesso ou falha.
     *
     * @param errorType   categoria do erro (ex: "AuthenticationFailed", "InvalidAddress")
     * @param errorDetail mensagem completa do erro para diagnóstico
     */
    public void saveLog(int deliveryId, String email, String status,
                        String errorType, String errorDetail) throws SQLException {
        String sql = "INSERT INTO delivery_logs (delivery_id, email, status, error_type, error_detail) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, deliveryId);
            stmt.setString(2, email);
            stmt.setString(3, status);
            stmt.setString(4, errorType);
            stmt.setString(5, errorDetail);
            stmt.executeUpdate();
        }
    }

    /** Lista todos os envios do utilizador com o nome da campanha */
    public List<Delivery> findByUserId(int userId) throws SQLException {
        List<Delivery> list = new ArrayList<>();
        String sql = """
            SELECT d.*, c.name as campaign_name
            FROM deliveries d
            LEFT JOIN campaigns c ON d.campaign_id = c.id
            WHERE d.user_id = ?
            ORDER BY d.id DESC
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /** Procura um delivery específico por ID */
    public Delivery findById(int id, int userId) throws SQLException {
        String sql = """
            SELECT d.*, c.name as campaign_name
            FROM deliveries d
            LEFT JOIN campaigns c ON d.campaign_id = c.id
            WHERE d.id = ? AND d.user_id = ?
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    /**
     * Devolve os logs detalhados de um envio.
     * Usado no histórico para mostrar exactamente que emails falharam e porquê.
     */
    public List<DeliveryLog> findLogs(int deliveryId) throws SQLException {
        List<DeliveryLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM delivery_logs WHERE delivery_id = ? ORDER BY id ASC";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, deliveryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(new DeliveryLog(
                        rs.getString("email"),
                        rs.getString("status"),
                        rs.getString("error_type"),
                        rs.getString("error_detail")
                    ));
                }
            }
        }
        return logs;
    }

    /** Devolve envios agendados que já devem ser executados */
    public List<Delivery> findScheduledReady() throws SQLException {
        List<Delivery> list = new ArrayList<>();
        String sql = """
            SELECT d.*, c.name as campaign_name
            FROM deliveries d
            LEFT JOIN campaigns c ON d.campaign_id = c.id
            WHERE d.status = 'scheduled'
            AND d.scheduled_at <= NOW()
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /** Soma de emails enviados e falhados — para o dashboard */
    public int[] getTotals(int userId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(sent),0), COALESCE(SUM(failed),0) " +
                     "FROM deliveries WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return new int[]{ rs.getInt(1), rs.getInt(2) };
            }
        }
        return new int[]{0, 0};
    }

    /** Conta envios do utilizador */
    public int countByUserId(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM deliveries WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Delivery map(ResultSet rs) throws SQLException {
        return new Delivery(
            rs.getInt("id"), rs.getInt("user_id"), rs.getInt("campaign_id"),
            rs.getString("campaign_name"), rs.getInt("total_contacts"),
            rs.getInt("sent"), rs.getInt("failed"),
            rs.getString("status"), rs.getString("created_at")
        );
    }

    /** Classe interna para representar um log individual de email */
    public record DeliveryLog(String email, String status, String errorType, String errorDetail) {}
}