package br.com.finkeeper.application.service;

import br.com.finkeeper.application.dto.CategorySpendingReportItem;
import br.com.finkeeper.application.dto.CategorySpendingReportResponse;
import br.com.finkeeper.domain.model.AppUser;
import br.com.finkeeper.domain.repository.EntryRepository;
import br.com.finkeeper.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório "onde eu mais gasto" (mercado, farmácia, passeios etc.) citado no
 * pedido original — agrega Entry por Category em um período.
 */
@Service
public class ReportService {

    private final EntryRepository entryRepository;
    private final CurrentUserProvider currentUserProvider;

    public ReportService(EntryRepository entryRepository, CurrentUserProvider currentUserProvider) {
        this.entryRepository = entryRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public CategorySpendingReportResponse spendingByCategory(LocalDate from, LocalDate to) {
        AppUser user = currentUserProvider.getCurrentUser();

        List<EntryRepository.CategorySpendingProjection> rows =
                entryRepository.sumSpendingByCategory(user.getId(), from, to);

        BigDecimal total = rows.stream()
                .map(EntryRepository.CategorySpendingProjection::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategorySpendingReportItem> items = rows.stream()
                .map(row -> new CategorySpendingReportItem(
                        row.getCategoryId(),
                        row.getCategoryName(),
                        row.getColorHex(),
                        row.getTotal(),
                        row.getEntryCount(),
                        percentageOf(row.getTotal(), total)))
                .toList();

        return new CategorySpendingReportResponse(from, to, total, items);
    }

    private BigDecimal percentageOf(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
