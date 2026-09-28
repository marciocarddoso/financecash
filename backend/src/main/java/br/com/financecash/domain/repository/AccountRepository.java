package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByOwnerIdAndActiveTrueOrderByNameAsc(UUID ownerId);
    List<Account> findByOwnerIdOrderByNameAsc(UUID ownerId);

    /** Usado pelo AccountSyncService para achar a conta já cadastrada de um banco (por nome + tipo) e não duplicar. */
    Optional<Account> findByOwnerIdAndBankNameIgnoreCaseAndType(UUID ownerId, String bankName, AccountType type);
}
