package com.mailbridge.services;

import com.mailbridge.dto.DTOs;
import com.mailbridge.exceptions.NotFoundException;
import com.mailbridge.exceptions.ValidationException;
import com.mailbridge.models.Campaign;
import com.mailbridge.repositories.CampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Serviço de campanhas — lógica de negócio entre o controller e o repositório.
 *
 * Responsabilidades:
 *   - Validar os dados antes de persistir
 *   - Converter DTOs em modelos de domínio
 *   - Garantir que o utilizador só acede às suas próprias campanhas
 */
public class CampaignService {
    private static final Logger logger = LoggerFactory.getLogger(CampaignService.class);
    private final CampaignRepository repo;

    public CampaignService(Connection connection) {
        this.repo = new CampaignRepository(connection);
    }

    /** Cria uma nova campanha a partir de um DTO de request */
    public void create(DTOs.CampaignRequest dto, int userId) throws SQLException {
        if (dto.name == null || dto.name.isBlank())
            throw new ValidationException("Nome da campanha é obrigatório");
        if (dto.subject == null || dto.subject.isBlank())
            throw new ValidationException("Assunto do email é obrigatório");
        if (dto.message == null || dto.message.isBlank())
            throw new ValidationException("Mensagem é obrigatória");

        Campaign c = new Campaign(0, userId, dto.name.trim(), dto.subject.trim(), dto.message.trim(), null);
        repo.save(c);
        logger.info("Campanha '{}' criada pelo user {}", c.getName(), userId);
    }

    /** Lista todas as campanhas do utilizador */
    public List<Campaign> getAll(int userId) throws SQLException {
        return repo.findByUserId(userId);
    }

    /** Procura uma campanha por ID — lança NotFoundException se não existir */
    public Campaign getById(int id, int userId) throws SQLException {
        Campaign c = repo.findById(id, userId);
        if (c == null) throw new NotFoundException("Campanha não encontrada");
        return c;
    }

    /** Elimina uma campanha */
    public void delete(int id, int userId) throws SQLException {
        boolean deleted = repo.deleteById(id, userId);
        if (!deleted) throw new NotFoundException("Campanha não encontrada");
    }

    /** Conta campanhas — usado nas estatísticas */
    public int count(int userId) throws SQLException {
        return repo.countByUserId(userId);
    }
}
