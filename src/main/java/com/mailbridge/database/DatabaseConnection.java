package com.mailbridge.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * Responsável por gerir a ligação à base de dados MariaDB.
 *
 * Padrão utilizado: Singleton — existe apenas uma ligação partilhada
 * por toda a aplicação, evitando o custo de abrir múltiplas ligações.
 *
 * As credenciais são lidas de db.properties e nunca hardcoded no código,
 * o que segue as boas práticas de segurança (configuração externa).
 */
public class DatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);

    // Instância única da ligação — reutilizada em todos os pedidos
    private static Connection connection;

    // Propriedades carregadas do ficheiro db.properties
    private static Properties appProps;

    /**
     * Devolve a ligação activa à base de dados.
     * Se a ligação não existir ou estiver fechada, cria uma nova.
     */
    public static Connection getConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                return connection;
            }

            Properties props = loadProperties();
            connection = DriverManager.getConnection(
                    props.getProperty("db.url"),
                    props.getProperty("db.user"),
                    props.getProperty("db.password")
            );

            logger.info("Ligação à base de dados estabelecida com sucesso");
            return connection;

        } catch (Exception e) {
            logger.error("Falha ao conectar à base de dados: {}", e.getMessage());
            throw new RuntimeException("Não foi possível ligar à base de dados", e);
        }
    }

    /**
     * Devolve as propriedades do ficheiro db.properties.
     * Usado pelo EmailService para ler as credenciais de email.
     */
    public static Properties getProperties() {
        if (appProps == null) appProps = loadProperties();
        return appProps;
    }

    /**
     * Lê o ficheiro db.properties do classpath (src/main/resources).
     * Se o ficheiro não existir, usa valores de fallback para desenvolvimento local.
     */
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = DatabaseConnection.class
                .getClassLoader().getResourceAsStream("db.properties")) {
            if (input != null) {
                props.load(input);
                logger.debug("db.properties carregado com sucesso");
            } else {
                logger.warn("db.properties não encontrado — a usar valores por defeito");
                props.setProperty("db.url",      "jdbc:mariadb://localhost:3300/mailbridge");
                props.setProperty("db.user",     "root");
                props.setProperty("db.password", "12345678");
            }
        } catch (Exception e) {
            logger.error("Erro ao ler db.properties: {}", e.getMessage());
        }
        return props;
    }
}
