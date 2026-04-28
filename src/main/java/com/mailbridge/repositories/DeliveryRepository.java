package com.mailbridge.repositories;

import com.mailbridge.models.Delivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório de envios — gere a tabela `deliveries`.
 *
 * Os deliveries são actualizados frequentemente durante o envio
 * (a cada email enviado), por isso o método updateProgress é chamado
 * muitas vezes numa operação de envio em massa.
 */
public class DeliveryRepository {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryRepository.class);
    private final Connection connection;

    public DeliveryRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Cria um registo de envio com status 'pending'.
     * Devolve o ID gerado para ser usado nas actualizações de progresso.
     */
    public int create(int userId, int campaignId, int totalContacts) throws SQLException {
        String sql = "INSERT INTO deliveries (user_id, campaign_id, total_contacts, sent, failed, status) " +
                     "VALUES (?, ?, ?, 0, 0, 'pending')";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, campaignId);
            stmt.setInt(3, totalContacts);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    logger.info("Delivery #{} criado — campanha {}, {} destinatários", id, campaignId, totalContacts);
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * Actualiza o progresso de um envio em curso.
     * Chamado após cada email enviado para manter o frontend actualizado via polling.
     */
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

    /** Lista todos os envios do utilizador com o nome da campanha (JOIN) */
    public List<Delivery> findByUserId(int userId) throws SQLException {
        List<Delivery> list = new ArrayList<>();
        // LEFT JOIN para incluir deliveries de campanhas entretanto eliminadas
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

    /** Procura um delivery específico — usado no polling de progresso */
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

    /** Soma total de emails enviados e falhados — para o dashboard */
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

    /** Conta envios do utilizador — para o dashboard */
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
}
