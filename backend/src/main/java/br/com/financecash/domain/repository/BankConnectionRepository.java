package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.BankConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {
    List<BankConnection> findByOwnerIdOrderByConnectedAtDesc(UUID ownerId);
    boolean existsByOwnerIdAndItemId(UUID ownerId, String itemId);
}
