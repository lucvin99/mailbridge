package com.mailbridge.services;

import com.mailbridge.database.DatabaseConnection;
import com.mailbridge.exceptions.EmailConfigException;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Serviço de envio de emails.
 *
 * Suporta dois modos controlados pela variável TEST_MODE:
 *   - TEST_MODE = true  → usa Mailpit local (porta 1025) para testes sem envio real
 *   - TEST_MODE = false → usa Gmail SMTP com as credenciais do db.properties
 *
 * Porquê App Password no Gmail?
 *   O Gmail com 2FA não permite login directo em apps de terceiros.
 *   A App Password é gerada em myaccount.google.com > Segurança > Passwords de aplicações.
 *
 * Protocolo: SMTP com STARTTLS na porta 587 (Gmail) ou porta 1025 (Mailpit).
 */
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    // Muda para false quando quiseres usar o Gmail real
    private static final boolean TEST_MODE = true;

    private final String from;
    private final String password;

    public EmailService() {
        Properties props = DatabaseConnection.getProperties();
        this.from     = props.getProperty("mail.from",     "").trim();
        this.password = props.getProperty("mail.password", "").trim();

        if (TEST_MODE) {
            logger.info("EmailService em MODO TESTE — a usar Mailpit em localhost:1025");
        } else if (isConfigured()) {
            logger.info("EmailService configurado — a enviar como: {}", from);
        } else {
            logger.warn("EmailService NÃO configurado — preenche mail.from e mail.password no db.properties");
        }
    }

    /**
     * Envia um email.
     * Em TEST_MODE usa o Mailpit local — não precisa de credenciais reais.
     * Em modo normal usa o Gmail SMTP com as credenciais do db.properties.
     */
    public void send(String toEmail, String toName, String subject, String body) throws MessagingException {
        if (TEST_MODE) {
            sendViaMailpit(toEmail, subject, body);
            return;
        }
        sendViaGmail(toEmail, subject, body);
    }

    /** Envia via Mailpit local — para testes sem envio real */
    private void sendViaMailpit(String toEmail, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth",            "false");
        props.put("mail.smtp.starttls.enable", "false");
        props.put("mail.smtp.host",            "localhost");
        props.put("mail.smtp.port",            "1025");

        Session session = Session.getInstance(props);
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("mailbridge@local.test"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject);
        message.setContent(buildHtml(body), "text/html; charset=utf-8");
        Transport.send(message);
        logger.debug("Email enviado para {} via Mailpit", toEmail);
    }

    /** Envia via Gmail SMTP com as credenciais do db.properties */
    private void sendViaGmail(String toEmail, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth",              "true");
        props.put("mail.smtp.starttls.enable",   "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.host",              "smtp.gmail.com");
        props.put("mail.smtp.port",              "587");
        props.put("mail.smtp.ssl.trust",         "smtp.gmail.com");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout",           "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        Message message = new MimeMessage(session);
        try {
            message.setFrom(new InternetAddress(from, "MailBridge", "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            message.setFrom(new InternetAddress(from));
        }
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject);
        message.setContent(buildHtml(body), "text/html; charset=utf-8");
        Transport.send(message);
        logger.debug("Email enviado para {} via Gmail", toEmail);
    }

    /** Converte texto simples em HTML ou passa HTML directamente */
    private String buildHtml(String body) {
        if (body.trim().startsWith("<")) return body;
        return "<div style='font-family:sans-serif;font-size:15px;line-height:1.7;color:#222;max-width:600px;'>"
                + body.replace("\n", "<br>") + "</div>";
    }

    public boolean isConfigured() {
        if (TEST_MODE) return true;
        return !from.isBlank() && !from.equals("o_teu_email@gmail.com")
            && !password.isBlank() && !password.equals("xxxx xxxx xxxx xxxx");
    }

    public void assertConfigured() {
        if (!isConfigured()) {
            throw new EmailConfigException(
                "Email não configurado. Abre o db.properties e preenche mail.from e mail.password."
            );
        }
    }
}