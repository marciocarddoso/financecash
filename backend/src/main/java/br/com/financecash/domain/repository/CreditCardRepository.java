package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {
    List<CreditCard> findByOwnerIdAndActiveTrueOrderByNameAsc(UUID ownerId);

    /** Usado pelo AccountSyncService para achar o cartão já cadastrado desse banco e não duplicar. */
    Optional<CreditCard> findByOwnerIdAndBankNameIgnoreCase(UUID ownerId, String bankName);
}
