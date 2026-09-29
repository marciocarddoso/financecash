package br.com.financecash.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Conta bancária, poupança ou de investimento (ex.: aplicação em CDI). */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    /** Para contas de investimento, ex.: "CDI 110%", "Tesouro Selic". Opcional. */
    @Column(name = "investment_description")
    private String investmentDescription;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * true = conta veio da sincronizacao Open Finance (Pluggy), so leitura pro usuario
     * (nome/banco/tipo/saldo controlados pelo sync; so o active pode ser alterado por ele).
     * false = conta criada manualmente, com CRUD completo liberado.
     */
    @Column(name = "synced_from_open_finance", nullable = false)
    @Builder.Default
    private boolean syncedFromOpenFinance = true;
}
