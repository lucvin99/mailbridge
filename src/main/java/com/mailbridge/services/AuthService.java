package com.mailbridge.services;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.mailbridge.exceptions.ValidationException;
import com.mailbridge.models.User;
import com.mailbridge.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Serviço de autenticação — gere registo e login de utilizadores.
 *
 * Segurança implementada:
 *   - Passwords encriptadas com BCrypt (factor 12) antes de guardar na BD
 *   - BCrypt inclui salt aleatório — duas passwords iguais geram hashes diferentes
 *   - Nunca guardamos nem comparamos passwords em texto simples
 *   - Mensagem de erro genérica no login ("Email ou password incorrectos")
 *     para não revelar se o email existe ou não (prevenção de user enumeration)
 */
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository repo;

    public AuthService(Connection connection) {
        this.repo = new UserRepository(connection);
    }

    /**
     * Regista um novo utilizador.
     * Valida os dados, encripta a password e guarda na base de dados.
     */
    public User register(String name, String email, String password) throws SQLException {
        // Validações de entrada
        if (name == null || name.isBlank())
            throw new ValidationException("Nome é obrigatório");
        if (email == null || !email.contains("@"))
            throw new ValidationException("Email inválido");
        if (password == null || password.length() < 6)
            throw new ValidationException("Password deve ter pelo menos 6 caracteres");
        if (repo.emailExists(email))
            throw new ValidationException("Este email já está registado");

        // BCrypt.hashToString(12, ...) — o 12 é o custo computacional
        // Maior custo = mais lento para atacantes por força bruta, mas ainda rápido para nós
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        User user = new User(0, name.trim(), email.trim().toLowerCase(), hash, null);
        repo.save(user);
        logger.info("Novo utilizador registado: {}", email);

        return repo.findByEmail(email);
    }

    /**
     * Autentica um utilizador.
     * Verifica o email e compara a password com o hash guardado.
     */
    public User login(String email, String password) throws SQLException {
        if (email == null || password == null)
            throw new ValidationException("Email e password são obrigatórios");

        User user = repo.findByEmail(email.trim().toLowerCase());

        // Mensagem genérica — não revela se o email existe
        if (user == null) {
            logger.warn("Tentativa de login com email não registado: {}", email);
            throw new ValidationException("Email ou password incorrectos");
        }

        // BCrypt.verifyer().verify() compara a password com o hash guardado
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            logger.warn("Password incorrecta para: {}", email);
            throw new ValidationException("Email ou password incorrectos");
        }

        logger.info("Login bem-sucedido: {}", email);
        return user;
    }
}
