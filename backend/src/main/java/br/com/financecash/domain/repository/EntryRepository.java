package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    List<Entry> findByOwnerIdAndDueDateBetweenOrderByDueDateAsc(UUID ownerId, LocalDate from, LocalDate to);

    List<Entry> findByOwnerIdAndDueDateAndStatusNotOrderByDescriptionAsc(UUID ownerId, LocalDate dueDate, EntryStatus excludedStatus);

    List<Entry> findByOwnerIdAndRecurringRuleIdOrderByDueDateDesc(UUID ownerId, UUID recurringRuleId);

    List<Entry> findByOwnerIdAndInstallmentPlanIdOrderByInstallmentNumberAsc(UUID ownerId, UUID installmentPlanId);

    @Query("""
            select e from Entry e
            where e.owner.id = :ownerId
              and e.status = 'PENDENTE'
              and e.dueDate between :from and :to
            """)
    List<Entry> findPendingBetween(@Param("ownerId") UUID ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select e.category.id as categoryId, e.category.name as categoryName, e.category.colorHex as colorHex,
                   sum(e.amount) as total, count(e) as entryCount
            from Entry e
            where e.owner.id = :ownerId
              and e.type = 'DESPESA'
              and e.dueDate between :from and :to
            group by e.category.id, e.category.name, e.category.colorHex
            order by sum(e.amount) desc
            """)
    List<CategorySpendingProjection> sumSpendingByCategory(@Param("ownerId") UUID ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    interface CategorySpendingProjection {
        UUID getCategoryId();
        String getCategoryName();
        String getColorHex();
        java.math.BigDecimal getTotal();
        long getEntryCount();
    }
}
