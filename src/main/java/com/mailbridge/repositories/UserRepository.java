package com.mailbridge.repositories;

import com.mailbridge.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Repositório de utilizadores — responsável por toda a comunicação
 * com a tabela `users` na base de dados.
 *
 * Padrão Repository: isola a lógica de acesso a dados do resto da aplicação.
 * Os services não sabem se os dados vêm de MariaDB, PostgreSQL ou outro — só
 * chamam métodos do repositório.
 *
 * Utiliza PreparedStatement em todas as queries para prevenir SQL Injection.
 */
public class UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);
    private final Connection connection;

    public UserRepository(Connection connection) {
        this.connection = connection;
    }

    /** Guarda um novo utilizador na base de dados */
    public void save(User user) throws SQLException {
        String sql = "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.executeUpdate();
            logger.info("Utilizador registado: {}", user.getEmail());
        }
    }

    /** Procura um utilizador pelo email — usado no login */
    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null; // null indica que o email não existe
    }

    /** Verifica se um email já está registado — evita duplicados */
    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Mapeia uma linha do ResultSet para um objecto User */
    private User map(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("created_at")
        );
    }
}
