package br.com.financecash.application.service;

import br.com.financecash.application.dto.MonthClosingCategoryItem;
import br.com.financecash.application.dto.MonthClosingResponse;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fase 1 do roadmap: "tela de fechamento do mês comparando previsto vs. realizado".
 *
 * Definições (por simplicidade, alinhadas com o que o Dashboard já faz — agrupar por
 * mês de VENCIMENTO, não de pagamento, já que a planilha original também nunca
 * separou os dois):
 * <ul>
 *   <li><b>previsto</b>: soma de todos os lançamentos com vencimento no mês, exceto
 *       os CANCELADOs — é "o que estava programado para o mês".</li>
 *   <li><b>realizado</b>: dentro desses, só os que já estão com status PAGO — é "o
 *       que de fato aconteceu".</li>
 * </ul>
 * Um lançamento pago antes ou depois do próprio mês de vencimento ainda conta no mês
 * do vencimento, não no mês do pagamento — mesma convenção do Dashboard.
 */
@Service
public class MonthClosingService {

    private final EntryRepository entryRepository;
    private final CurrentUserProvider currentUserProvider;

    public MonthClosingService(EntryRepository entryRepository, CurrentUserProvider currentUserProvider) {
        this.entryRepository = entryRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public MonthClosingResponse getMonthClosing(int year, int month) {
        AppUser user = currentUserProvider.getCurrentUser();
        YearMonth yearMonth = YearMonth.of(year, month);

        List<Entry> entries = entryRepository
                .findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(user.getId(), yearMonth.atDay(1), yearMonth.atEndOfMonth())
                .stream()
                .filter(e -> e.getStatus() != EntryStatus.CANCELADO)
                .toList();

        BigDecimal previstoReceita = sum(entries, e -> e.getType() == EntryType.RECEITA);
        BigDecimal realizadoReceita = sum(entries, e -> e.getType() == EntryType.RECEITA && e.getStatus() == EntryStatus.PAGO);
        BigDecimal previstoDespesa = sum(entries, e -> e.getType() == EntryType.DESPESA);
        BigDecimal realizadoDespesa = sum(entries, e -> e.getType() == EntryType.DESPESA && e.getStatus() == EntryStatus.PAGO);

        List<MonthClosingCategoryItem> categorias = byCategory(entries);

        return new MonthClosingResponse(year, month, previstoReceita, realizadoReceita, previstoDespesa, realizadoDespesa, categorias);
    }

    private List<MonthClosingCategoryItem> byCategory(List<Entry> entries) {
        record Key(UUID categoryId, EntryType type) {}

        Map<Key, List<Entry>> grouped = new LinkedHashMap<>();
        for (Entry entry : entries) {
            Key key = new Key(entry.getCategory().getId(), entry.getType());
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(entry);
        }

        return grouped.entrySet().stream()
                .map(e -> {
                    Entry sample = e.getValue().get(0);
                    BigDecimal previsto = sum(e.getValue(), en -> true);
                    BigDecimal realizado = sum(e.getValue(), en -> en.getStatus() == EntryStatus.PAGO);
                    return new MonthClosingCategoryItem(
                            sample.getCategory().getId(), sample.getCategory().getName(),
                            sample.getCategory().getColorHex(), sample.getType(), previsto, realizado);
                })
                .sorted(Comparator.comparing(MonthClosingCategoryItem::previsto).reversed())
                .toList();
    }

    private BigDecimal sum(List<Entry> entries, java.util.function.Predicate<Entry> filter) {
        return entries.stream()
                .filter(filter)
                .map(Entry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
