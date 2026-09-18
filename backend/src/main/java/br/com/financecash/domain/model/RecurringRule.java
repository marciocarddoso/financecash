package br.com.financecash.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Algo que se repete: salário, aluguel, conta fixa, décimo terceiro. O valor não
 * fica fixo aqui — fica em {@link RecurringRuleValueHistory}, para suportar
 * reajustes anuais/semestrais sem perder o valor praticado em cada período.
 */
@Entity
@Table(name = "recurring_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringRule {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntryType type;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceFrequency frequency;

    /** Dia do vencimento (1-31, ajustado para o último dia do mês quando necessário). */
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    /**
     * Para frequência SEMESTRAL/ANUAL/BIMESTRAL/TRIMESTRAL: em quais meses (1-12) a
     * regra ocorre. Ex.: décimo terceiro = [11, 12]. Para MENSAL fica vazio (todo mês).
     */
    @ElementCollection
    @CollectionTable(name = "recurring_rule_reference_month", joinColumns = @JoinColumn(name = "recurring_rule_id"))
    @Column(name = "month")
    @Builder.Default
    private List<Integer> referenceMonths = new ArrayList<>();

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "recurringRule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecurringRuleValueHistory> valueHistory = new ArrayList<>();
}
