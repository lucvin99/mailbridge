package com.mailbridge.services;

import com.mailbridge.database.DatabaseConnection;
import com.mailbridge.exceptions.EmailConfigException;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Serviço de envio de emails via SMTP do Gmail.
 *
 * Configuração necessária em db.properties:
 *   mail.from     — o teu endereço Gmail
 *   mail.password — App Password de 16 caracteres (NÃO a password normal)
 *
 * Porquê App Password?
 *   O Gmail com autenticação de 2 factores não permite login com a password
 *   normal em aplicações de terceiros. A App Password é uma password separada
 *   gerada pelo Google especificamente para este fim.
 *
 * Protocolo usado: SMTP com STARTTLS na porta 587
 *   - STARTTLS encripta a ligação após o handshake inicial
 *   - Mais compatível que SSL directo na porta 465
 */
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final String from;
    private final String password;

    public EmailService() {
        Properties props = DatabaseConnection.getProperties();
        this.from     = props.getProperty("mail.from",     "").trim();
        this.password = props.getProperty("mail.password", "").trim();

        if (isConfigured()) {
            logger.info("EmailService configurado — a enviar como: {}", from);
        } else {
            logger.warn("EmailService NÃO configurado — preenche mail.from e mail.password no db.properties");
        }
    }

    /**
     * Envia um email para um destinatário.
     *
     * @param toEmail  endereço do destinatário
     * @param toName   nome do destinatário (usado no cabeçalho To:)
     * @param subject  assunto do email
     * @param body     corpo do email (suporta HTML)
     */
    public void send(String toEmail, String toName, String subject, String body) throws MessagingException {
        // Configuração da ligação SMTP
        Properties props = new Properties();
        props.put("mail.smtp.auth",              "true");
        props.put("mail.smtp.starttls.enable",   "true");
        props.put("mail.smtp.starttls.required", "true");  // Obriga STARTTLS — rejeita ligações não encriptadas
        props.put("mail.smtp.host",              "smtp.gmail.com");
        props.put("mail.smtp.port",              "587");
        props.put("mail.smtp.ssl.trust",         "smtp.gmail.com");
        props.put("mail.smtp.connectiontimeout", "10000"); // Timeout de 10s para não bloquear indefinidamente
        props.put("mail.smtp.timeout",           "10000");

        // Cria sessão com autenticação — as credenciais são injectadas aqui
        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        Message message = new MimeMessage(session);

        try {
            // "MailBridge" é o nome que aparece no cliente de email do destinatário
            message.setFrom(new InternetAddress(from, "MailBridge", "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            message.setFrom(new InternetAddress(from));
        }

        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject);

        // Se o corpo começa com '<', assume HTML; caso contrário converte quebras de linha em <br>
        String htmlBody = body.trim().startsWith("<") ? body :
                "<div style='font-family:sans-serif;font-size:15px;line-height:1.7;color:#222;max-width:600px;'>" +
                body.replace("\n", "<br>") + "</div>";

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=utf-8");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(htmlPart);
        message.setContent(multipart);

        Transport.send(message);
    }

    /**
     * Verifica se o serviço está configurado com credenciais reais.
     * Chamado antes de iniciar um envio para dar feedback claro ao utilizador.
     */
    public boolean isConfigured() {
        return !from.isBlank() && !from.equals("o_teu_email@gmail.com")
            && !password.isBlank() && !password.equals("xxxx xxxx xxxx xxxx");
    }

    /** Lança excepção se não estiver configurado — chamado no início de um envio */
    public void assertConfigured() {
        if (!isConfigured()) {
            throw new EmailConfigException(
                "Email não configurado. Abre o ficheiro db.properties e preenche mail.from e mail.password."
            );
        }
    }
}
