package br.com.finkeeper.domain.repository;

import br.com.finkeeper.domain.model.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, UUID> {
    List<InstallmentPlan> findByOwnerIdOrderByFirstDueDateDesc(UUID ownerId);
}
