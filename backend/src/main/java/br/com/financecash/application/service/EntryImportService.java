package br.com.financecash.application.service;

import br.com.financecash.application.dto.EntryImportRowError;
import br.com.financecash.application.dto.EntryImportSummary;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.CategoryType;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Importação em lote de lançamentos via CSV — primeiro passo da Fase 1 do roadmap
 * ("importar o histórico atual da planilha"). Formato esperado, com cabeçalho:
 *
 * <pre>description,amount,dueDate,type,categoryName,origin</pre>
 *
 * <ul>
 *   <li>{@code description}: texto livre. Convenção usada pelo importador da
 *       planilha ({@code scripts/import_numbers_to_csv.py}): prefixar com o nome
 *       do banco/cartão (ex.: "Bradesco - Uber"), já que não existe coluna própria
 *       de banco — isso evita ter que adivinhar dia de fechamento/vencimento do
 *       cartão durante a importação.</li>
 *   <li>{@code amount}: número decimal, ponto como separador (ex.: 129.90).</li>
 *   <li>{@code dueDate}: ISO-8601 (aaaa-MM-dd).</li>
 *   <li>{@code type}: RECEITA ou DESPESA.</li>
 *   <li>{@code categoryName}: nome da categoria; criada automaticamente para o
 *       usuário atual se ainda não existir.</li>
 *   <li>{@code origin}: opcional — MANUAL, RECORRENCIA, PARCELAMENTO,
 *       IMPORTADO_BOLETO, IMPORTADO_CARTAO (default MANUAL se vazio).</li>
 * </ul>
 *
 * Linhas com erro (categoria/valor/data inválidos) não interrompem a importação —
 * ficam registradas em {@link EntryImportSummary#errors()} com o número da linha e
 * o motivo. Linhas cujo (description, dueDate, amount) já existe para o usuário são
 * puladas silenciosamente como duplicatas, para permitir reprocessar o mesmo
 * arquivo (ex.: uma nova exportação da planilha) sem duplicar lançamentos.
 */
@Service
public class EntryImportService {

    private final EntryRepository entryRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;

    public EntryImportService(EntryRepository entryRepository, CategoryRepository categoryRepository,
                               CurrentUserProvider currentUserProvider) {
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public EntryImportSummary importCsv(MultipartFile file) {
        AppUser user = currentUserProvider.getCurrentUser();

        int imported = 0;
        int duplicates = 0;
        List<EntryImportRowError> errors = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            for (CSVRecord record : parser) {
                long line = record.getRecordNumber() + 1; // +1: contabiliza o cabeçalho
                try {
                    if (tryImportRow(record, user)) {
                        imported++;
                    } else {
                        duplicates++;
                    }
                } catch (RuntimeException rowError) {
                    errors.add(new EntryImportRowError(line, rowError.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo CSV: " + e.getMessage(), e);
        }

        return new EntryImportSummary(imported, duplicates, errors);
    }

    private boolean tryImportRow(CSVRecord record, AppUser user) {
        String description = requireText(record, "description");
        BigDecimal amount = requireAmount(record, "amount");
        LocalDate dueDate = requireDate(record, "dueDate");
        EntryType type = requireEnum(record, "type", EntryType.class);
        String categoryName = requireText(record, "categoryName");
        EntryOrigin origin = optionalEnum(record, "origin", EntryOrigin.class, EntryOrigin.MANUAL);

        if (entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(user.getId(), description, dueDate, amount)) {
            return false;
        }

        Category category = categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), categoryName)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .owner(user)
                        .name(categoryName)
                        .type(type == EntryType.RECEITA ? CategoryType.RECEITA : CategoryType.DESPESA)
                        .active(true)
                        .build()));

        Entry entry = Entry.builder()
                .owner(user)
                .description(description)
                .amount(amount)
                .dueDate(dueDate)
                .type(type)
                .status(EntryStatus.PENDENTE)
                .origin(origin)
                .category(category)
                .build();

        entryRepository.save(entry);
        return true;
    }

    private String requireText(CSVRecord record, String column) {
        String value = safeGet(record, column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente: " + column);
        }
        return value;
    }

    private BigDecimal requireAmount(CSVRecord record, String column) {
        String value = requireText(record, column);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido em '" + column + "': " + value);
        }
    }

    private LocalDate requireDate(CSVRecord record, String column) {
        String value = requireText(record, column);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida em '" + column + "' (use aaaa-MM-dd): " + value);
        }
    }

    private <E extends Enum<E>> E requireEnum(CSVRecord record, String column, Class<E> enumType) {
        String value = requireText(record, column);
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valor inválido em '" + column + "': " + value);
        }
    }

    private <E extends Enum<E>> E optionalEnum(CSVRecord record, String column, Class<E> enumType, E defaultValue) {
        String value = safeGet(record, column);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valor inválido em '" + column + "': " + value);
        }
    }

    private String safeGet(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }
        String value = record.get(column);
        return value != null ? value.trim() : null;
    }
}
