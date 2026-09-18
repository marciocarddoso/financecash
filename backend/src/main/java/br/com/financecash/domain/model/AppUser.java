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
 * Usuário do sistema. Todo dado financeiro é escopado por AppUser — hoje existe um
 * único usuário real, mas o modelo já nasce multiusuário pensando no app mobile
 * (ver docs/ROADMAP.md, Fase 4/5).
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Alerta de conta vencendo nas próximas 24-48h (job diário) — ver NotificationService. */
    @Column(name = "notify_due_soon_email", nullable = false)
    @Builder.Default
    private boolean notifyDueSoonEmail = true;

    /** Alerta de projeção de saldo negativo no mês (job diário) — ver NotificationService. */
    @Column(name = "notify_negative_balance_email", nullable = false)
    @Builder.Default
    private boolean notifyNegativeBalanceEmail = true;

    /**
     * Preferência do usuário por SMS além de e-mail. Guardada desde já para não exigir
     * migração de schema quando um provedor de SMS for integrado, mas nesta versão
     * nenhum SMS é enviado de fato — ver Javadoc de NotificationService.
     */
    @Column(name = "notify_sms_enabled", nullable = false)
    @Builder.Default
    private boolean notifySmsEnabled = false;

    @Column(name = "phone_number")
    private String phoneNumber;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
