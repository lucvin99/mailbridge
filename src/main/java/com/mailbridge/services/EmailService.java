package com.mailbridge.services;

import com.mailbridge.database.DatabaseConnection;
import com.mailbridge.exceptions.EmailConfigException;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Serviço de envio de emails via SMTP.
 *
 * Suporta dois modos configurados em db.properties (mail.test_mode):
 *
 *   mail.test_mode=true  → Mailpit local (porta 1025)
 *                          Não envia emails reais — ideal para testes.
 *                          Requer Mailpit a correr: https://github.com/axllent/mailpit
 *
 *   mail.test_mode=false → Gmail SMTP (porta 587, STARTTLS)
 *                          Envia emails reais com as credenciais do db.properties.
 *                          Requer App Password do Google (não a password normal).
 *
 * Porquê App Password?
 *   O Gmail com verificação em dois passos bloqueia logins directos de apps.
 *   A App Password é gerada em: myaccount.google.com > Segurança > Passwords de aplicações
 */
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final boolean testMode;
    private final String from;
    private final String password;

    public EmailService() {
        Properties props = DatabaseConnection.getProperties();
        this.from     = props.getProperty("mail.from",      "").trim();
        this.password = props.getProperty("mail.password",  "").trim();
        // Lê o modo de teste do db.properties — sem tocar no código
        this.testMode = "true".equalsIgnoreCase(props.getProperty("mail.test_mode", "false").trim());

        if (testMode) {
            logger.info("EmailService: MODO TESTE activo — emails enviados para Mailpit em localhost:1025");
        } else if (isConfigured()) {
            logger.info("EmailService: configurado para envio real como {}", from);
        } else {
            logger.warn("EmailService: credenciais em falta — configura mail.from e mail.password no db.properties");
        }
    }

    /**
     * Envia um email para um destinatário.
     * O modo (teste ou real) é determinado pela configuração em db.properties.
     *
     * @param toEmail  endereço do destinatário
     * @param toName   nome do destinatário (usado no cabeçalho To:)
     * @param subject  assunto do email
     * @param body     corpo do email (HTML ou texto simples)
     */
    public void send(String toEmail, String toName, String subject, String body) throws MessagingException {
        if (testMode) {
            sendViaMailpit(toEmail, subject, body);
        } else {
            sendViaGmail(toEmail, subject, body);
        }
    }

    /**
     * Envia via Mailpit (servidor SMTP local para testes).
     * Não requer autenticação — aceita qualquer email sem o entregar de verdade.
     * Ver emails capturados em: http://localhost:8025
     */
    private void sendViaMailpit(String toEmail, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", "localhost");
        props.put("mail.smtp.port", "1025");
        props.put("mail.smtp.auth", "false");

        Session session = Session.getInstance(props);
        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress("mailbridge@local.test", false));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        msg.setSubject(subject);
        msg.setContent(buildHtml(body), "text/html; charset=utf-8");
        Transport.send(msg);
        logger.debug("Mailpit: email enviado para {}", toEmail);
    }

    /**
     * Envia via Gmail SMTP com autenticação STARTTLS.
     * Usa as credenciais definidas em db.properties.
     */
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

        Message msg = new MimeMessage(session);
        try {
            msg.setFrom(new InternetAddress(from, "MailBridge", "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            msg.setFrom(new InternetAddress(from));
        }
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        msg.setSubject(subject);
        msg.setContent(buildHtml(body), "text/html; charset=utf-8");
        Transport.send(msg);
        logger.debug("Gmail: email enviado para {}", toEmail);
    }

    /**
     * Converte o corpo do email para HTML.
     * Se já começa com '<', assume que é HTML (do editor GrapesJS).
     * Caso contrário, converte texto simples em HTML básico.
     */
    private String buildHtml(String body) {
        if (body.trim().startsWith("<")) return body;
        return "<div style='font-family:sans-serif;font-size:15px;line-height:1.7;color:#222;max-width:600px;'>"
                + body.replace("\n", "<br>") + "</div>";
    }

    /**
     * Verifica se o serviço está configurado com credenciais reais.
     * Em modo teste retorna sempre true — não precisa de credenciais.
     */
    public boolean isConfigured() {
        if (testMode) return true;
        return !from.isBlank()
            && !from.equals("o_teu_email@gmail.com")
            && !password.isBlank()
            && !password.equals("xxxx xxxx xxxx xxxx");
    }

    /** Lança excepção se não estiver configurado — chamado antes de iniciar um envio */
    public void assertConfigured() {
        if (!isConfigured()) {
            throw new EmailConfigException(
                "Email não configurado. Edita o ficheiro db.properties com mail.from e mail.password."
            );
        }
    }
}