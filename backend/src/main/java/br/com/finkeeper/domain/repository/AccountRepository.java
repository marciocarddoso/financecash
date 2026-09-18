package br.com.finkeeper.domain.repository;

import br.com.finkeeper.domain.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByOwnerIdAndActiveTrueOrderByNameAsc(UUID ownerId);
    List<Account> findByOwnerIdOrderByNameAsc(UUID ownerId);
}
