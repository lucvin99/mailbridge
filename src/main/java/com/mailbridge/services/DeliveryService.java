package com.mailbridge.services;

import com.mailbridge.exceptions.EmailConfigException;
import com.mailbridge.exceptions.NotFoundException;
import com.mailbridge.exceptions.ValidationException;
import com.mailbridge.models.Campaign;
import com.mailbridge.models.Delivery;
import com.mailbridge.models.Recipient;
import com.mailbridge.repositories.CampaignRepository;
import com.mailbridge.repositories.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço de envios — núcleo da aplicação.
 *
 * Fluxo completo de um envio:
 *   1. Utilizador carrega ficheiro → storeFile() guarda em memória e devolve fileKey
 *   2. Utilizador escolhe campanha e confirma → startDelivery() inicia o envio
 *   3. O envio corre numa thread separada para não bloquear o servidor HTTP
 *   4. A cada email enviado, o progresso é guardado na BD
 *   5. O frontend faz polling a cada 2s para mostrar o progresso em tempo real
 *
 * Porquê fileStore em memória e não na BD?
 *   Os destinatários são temporários — usados uma vez e descartados.
 *   Não persistir os dados pessoais da lista é também uma boa prática de privacidade.
 */
public class DeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepo;
    private final CampaignRepository campaignRepo;
    private final EmailService emailService;
    private final FileParserService parser;

    // Armazenamento temporário em memória: fileKey → lista de destinatários
    // ConcurrentHashMap é thread-safe — várias threads podem aceder simultaneamente
    private static final Map<String, List<Recipient>> fileStore = new ConcurrentHashMap<>();

    public DeliveryService(Connection connection) {
        this.deliveryRepo = new DeliveryRepository(connection);
        this.campaignRepo = new CampaignRepository(connection);
        this.emailService = new EmailService();
        this.parser       = new FileParserService();
    }

    /**
     * Processa o ficheiro carregado e guarda os destinatários em memória.
     * Devolve uma fileKey que o frontend usa para referenciar a lista no envio.
     */
    public String storeFile(byte[] data, String fileName) {
        List<Recipient> recipients;
        String lower = fileName.toLowerCase();

        if (lower.endsWith(".csv")) {
            recipients = parser.parseCsv(data);
        } else if (lower.endsWith(".xlsx")) {
            recipients = parser.parseExcel(data, true);
        } else if (lower.endsWith(".xls")) {
            recipients = parser.parseExcel(data, false);
        } else {
            throw new ValidationException("Formato não suportado. Usa CSV, XLS ou XLSX.");
        }

        if (recipients.isEmpty()) {
            throw new ValidationException("Nenhum destinatário válido encontrado no ficheiro.");
        }

        String fileKey = UUID.randomUUID().toString();
        fileStore.put(fileKey, recipients);
        logger.info("Ficheiro '{}' processado — {} destinatários, key: {}", fileName, recipients.size(), fileKey);
        return fileKey;
    }

    /** Devolve os destinatários associados a uma fileKey sem os remover */
    public List<Recipient> getRecipients(String fileKey) {
        return fileStore.get(fileKey);
    }

    /**
     * Inicia um envio em background.
     * Valida tudo antes de criar o registo e iniciar a thread.
     */
    public int startDelivery(int campaignId, String fileKey, int userId) throws SQLException {
        emailService.assertConfigured();

        Campaign campaign = campaignRepo.findById(campaignId, userId);
        if (campaign == null) throw new NotFoundException("Campanha não encontrada");

        // Remove da memória após usar — cada fileKey é de uso único
        List<Recipient> recipients = fileStore.remove(fileKey);
        if (recipients == null || recipients.isEmpty())
            throw new ValidationException("Ficheiro não encontrado ou já utilizado. Faz upload novamente.");

        // null = envio imediato (sem agendamento)
        int deliveryId = deliveryRepo.create(userId, campaignId, recipients.size(), null);

        final Campaign finalCampaign = campaign;
        final List<Recipient> finalRecipients = recipients;
        new Thread(() -> sendEmails(deliveryId, finalCampaign, finalRecipients)).start();

        logger.info("Delivery #{} iniciado — '{}', {} destinatários", deliveryId, campaign.getName(), recipients.size());
        return deliveryId;
    }

    /**
     * Loop de envio — corre em background numa thread separada.
     *
     * Para cada destinatário:
     *   1. Personaliza o assunto e corpo com as variáveis do ficheiro
     *   2. Tenta enviar o email
     *   3. Guarda o resultado (sucesso ou falha) na tabela delivery_logs
     *   4. Actualiza o progresso na tabela deliveries
     *   5. Aguarda 300ms para não ultrapassar limites do Gmail (~500/dia)
     */
    private void sendEmails(int deliveryId, Campaign campaign, List<Recipient> recipients) {
        int sent = 0, failed = 0;

        try {
            deliveryRepo.updateProgress(deliveryId, 0, 0, "running");
        } catch (SQLException e) {
            logger.error("Erro ao actualizar estado inicial do delivery #{}", deliveryId, e);
        }

        for (Recipient recipient : recipients) {
            try {
                // Substitui {{name}}, {{email}} e qualquer variável dinâmica do ficheiro
                String subject = personalize(campaign.getSubject(), recipient);
                String body    = personalize(campaign.getMessage(), recipient);

                emailService.send(recipient.getEmail(), recipient.getName(), subject, body);
                sent++;
                logger.info("[Delivery #{}] ✓ Enviado → {}", deliveryId, recipient.getEmail());

                // Regista sucesso no log detalhado
                try {
                    deliveryRepo.saveLog(deliveryId, recipient.getEmail(), "sent", null, null);
                } catch (SQLException logEx) {
                    logger.error("Erro ao guardar log de sucesso", logEx);
                }

                // Pausa entre envios para não ultrapassar limites do Gmail
                Thread.sleep(300);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[Delivery #{}] Thread interrompida", deliveryId);
                break;

            } catch (Exception e) {
                failed++;
                // Classifica o erro para mostrar diagnóstico útil no histórico
                String errorType   = classifyError(e);
                String errorDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                logger.warn("[Delivery #{}] ✗ Falhou → {} — {}", deliveryId, recipient.getEmail(), errorDetail);

                // Regista falha no log detalhado com tipo e detalhe do erro
                try {
                    deliveryRepo.saveLog(deliveryId, recipient.getEmail(), "failed", errorType, errorDetail);
                } catch (SQLException logEx) {
                    logger.error("Erro ao guardar log de falha", logEx);
                }
            }

            // Actualiza progresso na BD após cada email — o frontend lê isto via polling
            try {
                deliveryRepo.updateProgress(deliveryId, sent, failed, "running");
            } catch (SQLException e) {
                logger.error("Erro ao actualizar progresso do delivery #{}", deliveryId, e);
            }
        }

        // Marca como concluído — "error" só se TODOS falharam
        try {
            String finalStatus = (failed == recipients.size()) ? "error" : "done";
            deliveryRepo.updateProgress(deliveryId, sent, failed, finalStatus);
            logger.info("[Delivery #{}] Concluído — ✓ {} enviados, ✗ {} falhados", deliveryId, sent, failed);
        } catch (SQLException e) {
            logger.error("Erro ao finalizar delivery #{}", deliveryId, e);
        }
    }

    /**
     * Substitui variáveis no template pelo valor real do destinatário.
     *
     * Variáveis base sempre disponíveis:
     *   {{name}}  → nome do destinatário
     *   {{email}} → email do destinatário
     *
     * Variáveis dinâmicas:
     *   Qualquer coluna extra do CSV/Excel (ex: {{empresa}}, {{cidade}})
     */
    private String personalize(String template, Recipient r) {
        String result = template
                .replace("{{name}}",  r.getName())
                .replace("{{email}}", r.getEmail());

        for (Map.Entry<String, String> entry : r.getFields().entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /**
     * Classifica o tipo de erro de envio para diagnóstico no histórico.
     *
     * Permite ao utilizador perceber o que correu mal e como resolver,
     * sem precisar de interpretar mensagens técnicas do JavaMail/SMTP.
     */
    private String classifyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("535") || msg.contains("badcredentials") || msg.contains("username and password"))
            return "AuthenticationFailed";
        if (msg.contains("550") || msg.contains("user unknown") || msg.contains("does not exist"))
            return "InvalidAddress";
        if (msg.contains("timeout") || msg.contains("timed out"))
            return "Timeout";
        if (msg.contains("connection refused") || msg.contains("unable to connect"))
            return "ConnectionRefused";
        if (msg.contains("quota") || msg.contains("rate limit"))
            return "RateLimitExceeded";
        return "UnknownError";
    }

    /** Devolve os logs detalhados de um envio — emails individuais com resultado */
    public List<DeliveryRepository.DeliveryLog> getLogs(int deliveryId) throws SQLException {
        return deliveryRepo.findLogs(deliveryId);
    }

    public List<Delivery> getAll(int userId) throws SQLException {
        return deliveryRepo.findByUserId(userId);
    }

    public Delivery getById(int id, int userId) throws SQLException {
        Delivery d = deliveryRepo.findById(id, userId);
        if (d == null) throw new NotFoundException("Envio não encontrado");
        return d;
    }

    public int[] getTotals(int userId) throws SQLException {
        return deliveryRepo.getTotals(userId);
    }

    public int count(int userId) throws SQLException {
        return deliveryRepo.countByUserId(userId);
    }
}