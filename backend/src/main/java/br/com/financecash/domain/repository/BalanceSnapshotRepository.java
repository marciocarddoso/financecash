package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.BalanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, UUID> {

    Optional<BalanceSnapshot> findFirstByAccountIdOrderByReferenceDateDescCreatedAtDesc(UUID accountId);

    List<BalanceSnapshot> findByAccountIdOrderByReferenceDateDesc(UUID accountId);

    /** Usado pelo AccountSyncService: se já existe snapshot do dia (ex.: sincronizou 2x no mesmo dia), substitui em vez de duplicar. */
    Optional<BalanceSnapshot> findByAccountIdAndReferenceDate(UUID accountId, LocalDate referenceDate);

    @org.springframework.data.jpa.repository.Query("""
            select bs from BalanceSnapshot bs
            where bs.account.owner.id = :ownerId
              and bs.referenceDate = (
                  select max(bs2.referenceDate) from BalanceSnapshot bs2 where bs2.account = bs.account
              )
            """)
    List<BalanceSnapshot> findLatestSnapshotPerAccount(UUID ownerId);
}
