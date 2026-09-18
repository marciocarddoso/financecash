package br.com.finkeeper.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Valor vigente de uma {@link RecurringRule} a partir de determinada data.
 * Permite registrar reajustes (anuais/semestrais) sem perder o valor
 * praticado em cada período anterior.
 */
@Entity
@Table(name = "recurring_rule_value_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringRuleValueHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_rule_id", nullable = false)
    private RecurringRule recurringRule;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
}
