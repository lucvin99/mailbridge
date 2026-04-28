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
 *   1. Utilizador carrega ficheiro → previewFile() devolve fileKey + contagem
 *   2. Utilizador escolhe campanha e confirma → startDelivery() inicia o envio
 *   3. O envio corre numa thread separada (virtual thread do Java 21)
 *   4. A cada email enviado, o progresso é guardado na BD
 *   5. O frontend faz polling a /api/deliveries/:id para mostrar o progresso
 *
 * Porquê thread separada?
 *   Enviar 1000 emails demora minutos. Se corresse na thread do HTTP request,
 *   o browser ficaria à espera e o pedido faria timeout. Com thread separada,
 *   o servidor responde imediatamente com o deliveryId e o envio continua em fundo.
 *
 * Porquê fileStore em memória?
 *   Os destinatários não são persistidos na BD — são temporários.
 *   A fileKey é gerada quando o ficheiro é carregado e usada no envio.
 *   Simplifica o schema e garante que a lista é usada apenas uma vez.
 */
public class DeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepo;
    private final CampaignRepository campaignRepo;
    private final EmailService emailService;
    private final FileParserService parser;

    // Armazenamento temporário em memória: fileKey → lista de destinatários
    // ConcurrentHashMap é thread-safe — necessário porque várias threads podem aceder simultaneamente
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

        // Gera chave única para este ficheiro
        String fileKey = UUID.randomUUID().toString();
        fileStore.put(fileKey, recipients);

        logger.info("Ficheiro '{}' processado — {} destinatários, key: {}", fileName, recipients.size(), fileKey);
        return fileKey;
    }

    /** Devolve os destinatários associados a uma fileKey */
    public List<Recipient> getRecipients(String fileKey) {
        return fileStore.get(fileKey);
    }

    /**
     * Inicia um envio em background.
     * Valida tudo antes de criar o registo e iniciar a thread.
     */
    public int startDelivery(int campaignId, String fileKey, int userId) throws SQLException {
        // Verifica configuração de email primeiro — erro rápido
        emailService.assertConfigured();

        Campaign campaign = campaignRepo.findById(campaignId, userId);
        if (campaign == null) throw new NotFoundException("Campanha não encontrada");

        List<Recipient> recipients = fileStore.remove(fileKey); // remove da memória após usar
        if (recipients == null || recipients.isEmpty())
            throw new ValidationException("Ficheiro não encontrado ou já utilizado. Faz upload novamente.");

        // Cria registo na BD com status 'pending'
        int deliveryId = deliveryRepo.create(userId, campaignId, recipients.size());

        // Inicia envio em thread virtual (Java 17+ com Project Loom preview, ou adapta para Thread normal)
        final Campaign finalCampaign = campaign;
        final List<Recipient> finalRecipients = recipients;
        new Thread(() -> sendEmails(deliveryId, finalCampaign, finalRecipients)).start();

        logger.info("Delivery #{} iniciado — '{}', {} destinatários", deliveryId, campaign.getName(), recipients.size());
        return deliveryId;
    }

    /**
     * Loop de envio — corre em background.
     * A cada email tenta enviar, actualiza o progresso e aguarda 300ms
     * para não ultrapassar os limites de envio do Gmail.
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
                // Personalização: substitui variáveis no assunto e corpo
                String subject = personalize(campaign.getSubject(), recipient);
                String body    = personalize(campaign.getMessage(), recipient);

                emailService.send(recipient.getEmail(), recipient.getName(), subject, body);
                sent++;
                logger.info("[Delivery #{}] ✓ Enviado → {}", deliveryId, recipient.getEmail());

                // Pausa entre envios — Gmail limita a ~500 emails/dia em contas normais
                Thread.sleep(300);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[Delivery #{}] Thread interrompida", deliveryId);
                break;
            } catch (Exception e) {
                failed++;
                logger.warn("[Delivery #{}] ✗ Falhou → {} — {}", deliveryId, recipient.getEmail(), e.getMessage());
            }

            // Actualiza progresso na BD após cada email
            try {
                deliveryRepo.updateProgress(deliveryId, sent, failed, "running");
            } catch (SQLException e) {
                logger.error("Erro ao actualizar progresso do delivery #{}", deliveryId, e);
            }
        }

        // Marca como concluído
        try {
            // "error" apenas se TODOS falharam; caso contrário "done"
            String finalStatus = (failed == recipients.size()) ? "error" : "done";
            deliveryRepo.updateProgress(deliveryId, sent, failed, finalStatus);
            logger.info("[Delivery #{}] Concluído — ✓ {} enviados, ✗ {} falhados", deliveryId, sent, failed);
        } catch (SQLException e) {
            logger.error("Erro ao finalizar delivery #{}", deliveryId, e);
        }
    }

    /**
     * Substitui variáveis no template pelo valor real do destinatário.
     * Variáveis suportadas: {{name}}, {{email}}
     */
    private String personalize(String template, Recipient r) {
        return template
                .replace("{{name}}",  r.getName())
                .replace("{{email}}", r.getEmail());
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
