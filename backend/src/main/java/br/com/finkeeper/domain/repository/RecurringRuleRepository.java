package br.com.finkeeper.domain.repository;

import br.com.finkeeper.domain.model.RecurringRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, UUID> {
    List<RecurringRule> findByOwnerIdAndActiveTrueOrderByNameAsc(UUID ownerId);
}
