package com.mailbridge.config;

import com.mailbridge.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestão de sessões em memória.
 *
 * Cada sessão é um token UUID aleatório associado a um utilizador.
 * O token é enviado no header Authorization: Bearer <token> em cada pedido.
 *
 * Porquê em memória e não na BD?
 *   Para um projecto académico é suficiente e mais simples.
 *   Em produção usaria-se JWT ou sessões em Redis para persistir entre reinicios.
 *
 * ConcurrentHashMap garante que sessões criadas em threads diferentes
 * não causam condições de corrida.
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final Map<String, User> sessions = new ConcurrentHashMap<>();

    /** Cria uma nova sessão e devolve o token */
    public static String createSession(User user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        logger.debug("Sessão criada para {}", user.getEmail());
        return token;
    }

    /** Devolve o utilizador associado ao token, ou null se inválido */
    public static User getUser(String token) {
        if (token == null) return null;
        return sessions.get(token);
    }

    /** Termina uma sessão (logout) */
    public static void invalidate(String token) {
        User user = sessions.remove(token);
        if (user != null) logger.info("Sessão terminada para {}", user.getEmail());
    }
}
