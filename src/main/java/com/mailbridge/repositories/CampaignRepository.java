package com.mailbridge.repositories;

import com.mailbridge.models.Campaign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório de campanhas — gere a tabela `campaigns`.
 *
 * Nota importante: todas as queries filtram por user_id.
 * Isto garante que um utilizador nunca acede a campanhas de outro,
 * mesmo que tente adivinhar o ID. Segurança por design.
 */
public class CampaignRepository {
    private static final Logger logger = LoggerFactory.getLogger(CampaignRepository.class);
    private final Connection connection;

    public CampaignRepository(Connection connection) {
        this.connection = connection;
    }

    /** Guarda uma nova campanha */
    public void save(Campaign c) throws SQLException {
        String sql = "INSERT INTO campaigns (user_id, name, subject, message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, c.getUserId());
            stmt.setString(2, c.getName());
            stmt.setString(3, c.getSubject());
            stmt.setString(4, c.getMessage());
            stmt.executeUpdate();
            logger.info("Campanha criada: '{}' (user {})", c.getName(), c.getUserId());
        }
    }

    /** Lista todas as campanhas do utilizador, da mais recente para a mais antiga */
    public List<Campaign> findByUserId(int userId) throws SQLException {
        List<Campaign> list = new ArrayList<>();
        String sql = "SELECT * FROM campaigns WHERE user_id = ? ORDER BY id DESC";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /** Procura uma campanha por ID — verifica também o user_id por segurança */
    public Campaign findById(int id, int userId) throws SQLException {
        String sql = "SELECT * FROM campaigns WHERE id = ? AND user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    /** Elimina uma campanha — verifica user_id para evitar que alguém apague campanhas alheias */
    public boolean deleteById(int id, int userId) throws SQLException {
        String sql = "DELETE FROM campaigns WHERE id = ? AND user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.setInt(2, userId);
            boolean deleted = stmt.executeUpdate() > 0;
            if (deleted) logger.info("Campanha {} eliminada (user {})", id, userId);
            return deleted;
        }
    }

    /** Conta campanhas do utilizador — usado nas estatísticas do dashboard */
    public int countByUserId(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM campaigns WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Campaign map(ResultSet rs) throws SQLException {
        return new Campaign(
            rs.getInt("id"), rs.getInt("user_id"),
            rs.getString("name"), rs.getString("subject"),
            rs.getString("message"), rs.getString("created_at")
        );
    }
}
