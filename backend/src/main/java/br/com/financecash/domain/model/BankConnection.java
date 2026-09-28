package br.com.financecash.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Conexão com um banco via Open Finance, feita através da Pluggy — ver
 * docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3. Guarda só o {@code itemId} que a
 * Pluggy usa para identificar essa conexão; nenhuma credencial do banco passa
 * pelo FinanceCash, isso fica inteiramente do lado da Pluggy.
 */
@Entity
@Table(name = "bank_connection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnection {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    /** Identificador da conexão na Pluggy (devolvido pelo widget no callback onSuccess). */
    @Column(name = "item_id", nullable = false, length = 100)
    private String itemId;

    /** Nome do banco/conector, obtido via PluggyClient.getItem ao salvar a conexão. */
    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankConnectionStatus status;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    /** Preenchido quando o AccountSyncService (próxima fase) sincronizar saldo/transações. */
    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @PrePersist
    void onCreate() {
        this.connectedAt = Instant.now();
    }
}
