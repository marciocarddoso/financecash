package br.com.finkeeper.domain.repository;

import br.com.finkeeper.domain.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByEntryId(UUID entryId);
}
