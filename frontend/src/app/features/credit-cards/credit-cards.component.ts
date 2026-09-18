import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CreditCardService } from '../../core/services/credit-card.service';
import { CreditCard } from '../../core/models/credit-card.model';

@Component({
  selector: 'fc-credit-cards',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <h1>Cartões de Crédito</h1>
    <p class="fc-hint">Cadastre seus cartões por banco/emissor para lançar as compras e acompanhar a fatura de cada um.</p>

    <form class="fc-card fc-inline-form" [formGroup]="form" (ngSubmit)="submit()">
      <input type="text" placeholder="Nome (ex.: Nubank Ultravioleta)" formControlName="name" />
      <input type="text" placeholder="Banco (ex.: Nubank)" formControlName="bankName" />
      <label>Fecha dia <input type="number" min="1" max="31" formControlName="closingDay" /></label>
      <label>Vence dia <input type="number" min="1" max="31" formControlName="dueDay" /></label>
      <button class="fc-button" type="submit" [disabled]="form.invalid">Adicionar cartão</button>
    </form>

    <div class="fc-card">
      @if (cards().length === 0) {
        <p>Nenhum cartão cadastrado ainda.</p>
      } @else {
        <table class="fc-table">
          <thead><tr><th>Nome</th><th>Banco</th><th>Fechamento</th><th>Vencimento</th></tr></thead>
          <tbody>
            @for (card of cards(); track card.id) {
              <tr>
                <td>{{ card.name }}</td>
                <td>{{ card.bankName }}</td>
                <td>dia {{ card.closingDay }}</td>
                <td>dia {{ card.dueDay }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: [`
    .fc-hint { color: var(--fc-color-text-muted); font-size: 0.9rem; margin-bottom: 1.25rem; }
    .fc-inline-form { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; }
    .fc-inline-form input[type="text"] { flex: 1; min-width: 160px; padding: 0.45rem 0.6rem; border: 1px solid var(--fc-color-border); border-radius: 6px; }
    .fc-inline-form label { display: flex; align-items: center; gap: 0.4rem; font-size: 0.85rem; color: var(--fc-color-text-muted); }
    .fc-inline-form input[type="number"] { width: 60px; padding: 0.45rem 0.5rem; border: 1px solid var(--fc-color-border); border-radius: 6px; }
  `],
})
export class CreditCardsComponent implements OnInit {
  private readonly creditCardService = inject(CreditCardService);
  private readonly fb = inject(FormBuilder);

  readonly cards = signal<CreditCard[]>([]);

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    bankName: ['', Validators.required],
    closingDay: [1, [Validators.required, Validators.min(1), Validators.max(31)]],
    dueDay: [10, [Validators.required, Validators.min(1), Validators.max(31)]],
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.creditCardService.list().subscribe((cards) => this.cards.set(cards));
  }

  submit(): void {
    if (this.form.invalid) return;
    this.creditCardService.create(this.form.getRawValue()).subscribe(() => {
      this.form.reset({ name: '', bankName: '', closingDay: 1, dueDay: 10 });
      this.reload();
    });
  }
}
