package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByEntryId(UUID entryId);
}
