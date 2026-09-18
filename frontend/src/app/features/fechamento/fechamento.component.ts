import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MonthClosingService } from '../../core/services/month-closing.service';
import { MonthClosingResponse } from '../../core/models/month-closing.model';

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

@Component({
  selector: 'fc-fechamento',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fc-header-row">
      <h1>Fechamento do mês</h1>
      <input type="month" [(ngModel)]="yearMonth" (change)="load()" />
    </div>

    <p class="fc-hint">
      "Previsto" soma tudo com vencimento no mês (menos o que foi cancelado); "realizado" é só o que já está
      marcado como pago.
    </p>

    @if (loading()) {
      <p>Carregando...</p>
    } @else {
      @if (closing(); as data) {
        <div class="fc-summary-cards">
          <div class="fc-card fc-summary-card">
            <h3>Receitas</h3>
            <p class="fc-summary-card__row"><span>Previsto</span><strong>{{ data.previstoReceita | currency: 'BRL' }}</strong></p>
            <p class="fc-summary-card__row"><span>Realizado</span><strong>{{ data.realizadoReceita | currency: 'BRL' }}</strong></p>
          </div>
          <div class="fc-card fc-summary-card">
            <h3>Despesas</h3>
            <p class="fc-summary-card__row"><span>Previsto</span><strong>{{ data.previstoDespesa | currency: 'BRL' }}</strong></p>
            <p class="fc-summary-card__row"><span>Realizado</span><strong>{{ data.realizadoDespesa | currency: 'BRL' }}</strong></p>
          </div>
          <div class="fc-card fc-summary-card">
            <h3>Saldo do mês</h3>
            <p class="fc-summary-card__row"><span>Previsto</span><strong>{{ (data.previstoReceita - data.previstoDespesa) | currency: 'BRL' }}</strong></p>
            <p class="fc-summary-card__row"><span>Realizado</span><strong>{{ (data.realizadoReceita - data.realizadoDespesa) | currency: 'BRL' }}</strong></p>
          </div>
        </div>

        <div class="fc-card">
          @if (data.categorias.length === 0) {
            <p>Nenhum lançamento neste mês.</p>
          } @else {
            <table class="fc-table">
              <thead>
                <tr><th>Categoria</th><th>Tipo</th><th>Previsto</th><th>Realizado</th><th>Diferença</th></tr>
              </thead>
              <tbody>
                @for (item of data.categorias; track item.categoryId + item.type) {
                  <tr>
                    <td>{{ item.categoryName }}</td>
                    <td>{{ item.type }}</td>
                    <td>{{ item.previsto | currency: 'BRL' }}</td>
                    <td>{{ item.realizado | currency: 'BRL' }}</td>
                    <td [class.fc-negative]="item.realizado - item.previsto < 0">
                      {{ (item.realizado - item.previsto) | currency: 'BRL' }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      }
    }
  `,
  styles: [`
    .fc-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; }
    .fc-header-row input { padding: 0.4rem 0.6rem; border: 1px solid var(--fc-color-border); border-radius: 6px; }
    .fc-hint { color: var(--fc-color-text-muted, #666); margin: 0 0 1.25rem; }
    .fc-summary-cards { display: flex; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
    .fc-summary-card { flex: 1; min-width: 200px; }
    .fc-summary-card h3 { margin: 0 0 0.75rem; font-size: 0.95rem; }
    .fc-summary-card__row { display: flex; justify-content: space-between; margin: 0.3rem 0; font-size: 0.9rem; }
    .fc-negative { color: var(--fc-color-danger); }
  `],
})
export class FechamentoComponent implements OnInit {
  private readonly monthClosingService = inject(MonthClosingService);

  yearMonth = currentYearMonth();
  readonly loading = signal(true);
  readonly closing = signal<MonthClosingResponse | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const [year, month] = this.yearMonth.split('-').map(Number);
    if (!year || !month) return;
    this.loading.set(true);
    this.monthClosingService.get(year, month).subscribe((data) => {
      this.closing.set(data);
      this.loading.set(false);
    });
  }
}
