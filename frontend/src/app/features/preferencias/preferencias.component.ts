import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { NotificationPreferencesService } from '../../core/services/notification-preferences.service';

@Component({
  selector: 'fc-preferencias',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <h1>Preferências de notificação</h1>

    <div class="fc-card">
      @if (loading()) {
        <p>Carregando...</p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="save()">
          <label class="fc-toggle-row">
            <input type="checkbox" formControlName="notifyDueSoonEmail" />
            <span>
              <strong>Contas vencendo</strong>
              <small>Recebe um e-mail quando houver contas pendentes vencendo nos próximos dias.</small>
            </span>
          </label>

          <label class="fc-toggle-row">
            <input type="checkbox" formControlName="notifyNegativeBalanceEmail" />
            <span>
              <strong>Projeção de saldo negativo</strong>
              <small>Recebe um e-mail quando a projeção de saldo do mês ficar negativa.</small>
            </span>
          </label>

          <label class="fc-toggle-row">
            <input type="checkbox" formControlName="notifySmsEnabled" />
            <span>
              <strong>Também por SMS</strong>
              <small>
                Ainda não enviamos SMS de verdade (nenhum provedor configurado) — a preferência já fica salva
                para quando isso existir.
              </small>
            </span>
          </label>

          <label class="fc-phone-field">
            Telefone (para o SMS futuro)
            <input type="tel" formControlName="phoneNumber" placeholder="+55 11 99999-9999" />
          </label>

          <button class="fc-button" type="submit" [disabled]="saving()">
            {{ saving() ? 'Salvando...' : 'Salvar' }}
          </button>
          @if (saved()) {
            <span class="fc-saved">Preferências salvas.</span>
          }
        </form>
      }
    </div>
  `,
  styles: [`
    form { display: flex; flex-direction: column; gap: 1.25rem; max-width: 520px; }
    .fc-toggle-row { display: flex; gap: 0.75rem; align-items: flex-start; cursor: pointer; }
    .fc-toggle-row input { margin-top: 0.2rem; }
    .fc-toggle-row span { display: flex; flex-direction: column; gap: 0.15rem; }
    .fc-toggle-row small { color: var(--fc-color-text-muted, #666); }
    .fc-phone-field { display: flex; flex-direction: column; gap: 0.3rem; font-size: 0.85rem; }
    .fc-phone-field input { padding: 0.45rem 0.6rem; border: 1px solid var(--fc-color-border); border-radius: 6px; max-width: 260px; }
    .fc-saved { margin-left: 0.75rem; color: var(--fc-color-success, #1e6f5c); }
  `],
})
export class PreferenciasComponent implements OnInit {
  private readonly preferencesService = inject(NotificationPreferencesService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly saved = signal(false);

  readonly form = this.fb.nonNullable.group({
    notifyDueSoonEmail: [true],
    notifyNegativeBalanceEmail: [true],
    notifySmsEnabled: [false],
    phoneNumber: [''],
  });

  ngOnInit(): void {
    this.preferencesService.get().subscribe((preferences) => {
      this.form.patchValue({
        notifyDueSoonEmail: preferences.notifyDueSoonEmail,
        notifyNegativeBalanceEmail: preferences.notifyNegativeBalanceEmail,
        notifySmsEnabled: preferences.notifySmsEnabled,
        phoneNumber: preferences.phoneNumber ?? '',
      });
      this.loading.set(false);
    });
  }

  save(): void {
    this.saving.set(true);
    this.saved.set(false);
    const value = this.form.getRawValue();
    this.preferencesService
      .update({ ...value, phoneNumber: value.phoneNumber || null })
      .subscribe(() => {
        this.saving.set(false);
        this.saved.set(true);
      });
  }
}
