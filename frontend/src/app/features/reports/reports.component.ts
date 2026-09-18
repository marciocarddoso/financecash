import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportService } from '../../core/services/report.service';
import { CategorySpendingReportResponse } from '../../core/models/report.model';

function firstDayOfMonth(date = new Date()): string {
  return new Date(date.getFullYear(), date.getMonth(), 1).toISOString().substring(0, 10);
}

function lastDayOfMonth(date = new Date()): string {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0).toISOString().substring(0, 10);
}

@Component({
  selector: 'fc-reports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h1>Relatórios</h1>
    <p class="fc-hint">Onde você mais gasta: mercado, farmácia, passeios e demais categorias, no período selecionado.</p>

    <div class="fc-card fc-inline-form">
      <label>De <input type="date" [(ngModel)]="from" /></label>
      <label>Até <input type="date" [(ngModel)]="to" /></label>
      <button class="fc-button" (click)="load()">Atualizar</button>
    </div>

    @if (report(); as r) {
      <div class="fc-card">
        <h2>Total gasto no período: {{ r.totalSpent | currency: 'BRL' }}</h2>

        @if (r.items.length === 0) {
          <p>Nenhuma despesa no período.</p>
        } @else {
          <table class="fc-table">
            <thead><tr><th>Categoria</th><th>Total</th><th>Lançamentos</th><th>% do total</th></tr></thead>
            <tbody>
              @for (item of r.items; track item.categoryId) {
                <tr>
                  <td>
                    <span class="fc-color-dot" [style.background]="item.colorHex ?? '#ccc'"></span>
                    {{ item.categoryName }}
                  </td>
                  <td>{{ item.total | currency: 'BRL' }}</td>
                  <td>{{ item.entryCount }}</td>
                  <td>
                    <div class="fc-bar-track">
                      <div class="fc-bar-fill" [style.width.%]="item.percentageOfTotal" [style.background]="item.colorHex ?? 'var(--fc-color-primary)'"></div>
                    </div>
                    {{ item.percentageOfTotal }}%
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    }
  `,
  styles: [`
    .fc-hint { color: var(--fc-color-text-muted); font-size: 0.9rem; margin-bottom: 1.25rem; }
    .fc-inline-form { display: flex; gap: 1rem; align-items: center; margin-bottom: 1.5rem; }
    .fc-inline-form label { display: flex; flex-direction: column; gap: 0.3rem; font-size: 0.85rem; color: var(--fc-color-text-muted); }
    .fc-inline-form input { padding: 0.4rem 0.6rem; border: 1px solid var(--fc-color-border); border-radius: 6px; }
    .fc-color-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 0.4rem; }
    .fc-bar-track { display: inline-block; width: 100px; height: 6px; background: var(--fc-color-border); border-radius: 3px; overflow: hidden; margin-right: 0.5rem; vertical-align: middle; }
    .fc-bar-fill { height: 100%; }
  `],
})
export class ReportsComponent implements OnInit {
  private readonly reportService = inject(ReportService);

  from = firstDayOfMonth();
  to = lastDayOfMonth();

  readonly report = signal<CategorySpendingReportResponse | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.reportService.spendingByCategory(this.from, this.to).subscribe((report) => this.report.set(report));
  }
}
