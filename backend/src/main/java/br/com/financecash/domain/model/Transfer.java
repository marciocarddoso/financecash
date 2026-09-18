package br.com.financecash.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Complemento de um Entry para transferências (ex.: PIX para as filhas), permitindo
 * relatório "quanto enviei e para quem" sem misturar com despesas de consumo.
 */
@Entity
@Table(name = "transfer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false, unique = true)
    private Entry entry;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    /** Chave PIX mascarada (ex.: "***.***.789-**"), nunca a chave completa. */
    @Column(name = "pix_key_masked")
    private String pixKeyMasked;

    @Column(nullable = false)
    @Builder.Default
    private boolean recurring = false;
}
