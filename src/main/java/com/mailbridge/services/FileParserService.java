package com.mailbridge.services;

import com.mailbridge.models.Recipient;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço de leitura de ficheiros CSV e Excel.
 *
 * Responsabilidade única: transformar um ficheiro num List<Recipient>.
 * Não sabe nada de emails, base de dados ou HTTP — só lê ficheiros.
 *
 * Regras de leitura:
 *   - Coluna 'email' (ou 'e-mail') é obrigatória
 *   - Coluna 'name' (ou 'nome') é opcional
 *   - Todas as outras colunas são lidas como campos dinâmicos ({{coluna}})
 *   - Linhas com email inválido são ignoradas e registadas no log
 */
public class FileParserService {
    private static final Logger logger = LoggerFactory.getLogger(FileParserService.class);

    /**
     * Lê o stream completamente para memória antes de processar.
     * Necessário porque o Apache POI e o Jetty têm conflito se o stream
     * for lido directamente do multipart sem buffer intermédio.
     */
    public static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /** Processa um ficheiro CSV e devolve a lista de destinatários válidos */
    public List<Recipient> parseCsv(byte[] data) {
        List<Recipient> recipients = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(
                new ByteArrayInputStream(data), StandardCharsets.UTF_8,
                CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord(true).withIgnoreHeaderCase().withTrim())) {

            for (CSVRecord record : parser) {
                String email = getField(record, "email", "e-mail");
                if (email == null || !isValidEmail(email)) {
                    logger.warn("CSV linha {} ignorada — email inválido: {}", record.getRecordNumber(), email);
                    continue;
                }
                String name = getField(record, "name", "nome");

                // Lê todas as colunas extra como campos dinâmicos
                Map<String, String> fields = new HashMap<>();
                for (String header : parser.getHeaderNames()) {
                    String key = header.toLowerCase().trim();
                    // Ignora as colunas base — já estão em name e email
                    if (key.equals("email") || key.equals("e-mail")
                            || key.equals("name") || key.equals("nome")) continue;
                    try {
                        String val = record.get(header);
                        if (val != null && !val.isBlank()) fields.put(key, val.trim());
                    } catch (Exception ignored) {}
                }

                recipients.add(new Recipient(
                        name != null ? name : emailToName(email),
                        email.toLowerCase(),
                        fields
                ));
            }

        } catch (Exception e) {
            logger.error("Erro ao processar CSV: {}", e.getMessage());
        }
        logger.info("CSV processado — {} destinatários válidos", recipients.size());
        return recipients;
    }

    /** Processa um ficheiro Excel (.xlsx ou .xls) e devolve a lista de destinatários */
    public List<Recipient> parseExcel(byte[] data, boolean isXlsx) {
        List<Recipient> recipients = new ArrayList<>();
        try (Workbook workbook = isXlsx
                ? new XSSFWorkbook(new ByteArrayInputStream(data))
                : new HSSFWorkbook(new ByteArrayInputStream(data))) {

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                logger.warn("Excel vazio ou sem cabeçalho");
                return recipients;
            }

            // Detecta as colunas pelo nome do cabeçalho
            int emailCol = -1, nameCol = -1;
            // Mapa: índice de coluna → nome da coluna (para campos dinâmicos)
            Map<Integer, String> extraCols = new HashMap<>();

            for (Cell cell : header) {
                String val = cell.getStringCellValue().toLowerCase().trim();
                int idx = cell.getColumnIndex();
                if (val.equals("email") || val.equals("e-mail")) {
                    emailCol = idx;
                } else if (val.equals("name") || val.equals("nome")) {
                    nameCol = idx;
                } else if (!val.isBlank()) {
                    // Regista coluna extra como campo dinâmico
                    extraCols.put(idx, val);
                }
            }

            if (emailCol == -1) {
                logger.error("Excel sem coluna 'email'");
                return recipients;
            }

            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String email = formatter.formatCellValue(row.getCell(emailCol)).trim();
                if (email.isBlank() || !isValidEmail(email)) {
                    logger.warn("Excel linha {} ignorada — email inválido: {}", i + 1, email);
                    continue;
                }

                String name = nameCol >= 0
                        ? formatter.formatCellValue(row.getCell(nameCol)).trim()
                        : null;

                // Lê todos os campos dinâmicos desta linha
                Map<String, String> fields = new HashMap<>();
                for (Map.Entry<Integer, String> entry : extraCols.entrySet()) {
                    Cell cell = row.getCell(entry.getKey());
                    if (cell != null) {
                        String val = formatter.formatCellValue(cell).trim();
                        if (!val.isBlank()) fields.put(entry.getValue(), val);
                    }
                }

                recipients.add(new Recipient(
                        (name != null && !name.isBlank()) ? name : emailToName(email),
                        email.toLowerCase(),
                        fields
                ));
            }

        } catch (Exception e) {
            logger.error("Erro ao processar Excel: {}", e.getMessage());
        }
        logger.info("Excel processado — {} destinatários válidos", recipients.size());
        return recipients;
    }

    /** Tenta ler um campo de um registo CSV por vários nomes alternativos */
    private String getField(CSVRecord r, String... keys) {
        for (String k : keys) {
            try {
                String v = r.get(k);
                if (v != null && !v.isBlank()) return v.trim();
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Validação básica de email — verifica formato mínimo */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".")
                && email.indexOf("@") < email.lastIndexOf(".");
    }

    /**
     * Converte a parte local de um email num nome legível.
     * Ex: "joao.silva@gmail.com" → "Joao Silva"
     */
    private String emailToName(String email) {
        String local = email.split("@")[0];
        String[] parts = local.split("[._-]");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                name.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) name.append(part.substring(1));
                name.append(" ");
            }
        }
        return name.toString().trim();
    }
}