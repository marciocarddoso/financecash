import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardService } from '../../core/services/dashboard.service';
import { EntryService } from '../../core/services/entry.service';
import { DashboardResponse } from '../../core/models/dashboard.model';
import { PendingInvoiceConfirmation } from '../../core/models/entry.model';

@Component({
  selector: 'fc-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly entryService = inject(EntryService);

  readonly dashboard = signal<DashboardResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Faturas de cartão fechadas ainda PENDENTE — aviso "já foi paga?" (01/10, confirmação manual
  // por fatura em vez de marcar como pago sozinho; ver PendingInvoiceConfirmation).
  readonly pendingInvoiceConfirmations = signal<PendingInvoiceConfirmation[]>([]);
  readonly confirmingInvoiceKey = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dashboardService.get().subscribe({
      next: (data) => {
        this.dashboard.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(
          'Não foi possível carregar o dashboard. Verifique se a API está rodando e se você está autenticado.',
        );
        this.loading.set(false);
      },
    });
    this.loadPendingInvoiceConfirmations();
  }

  private loadPendingInvoiceConfirmations(): void {
    this.entryService.pendingInvoiceConfirmations().subscribe({
      next: (confirmations) => this.pendingInvoiceConfirmations.set(confirmations),
      // Falha aqui não deve travar o resto do dashboard — só o aviso fica ausente.
      error: () => this.pendingInvoiceConfirmations.set([]),
    });
  }

  invoiceKey(confirmation: PendingInvoiceConfirmation): string {
    return `${confirmation.creditCardId}-${confirmation.dueDate}`;
  }

  confirmInvoicePaid(confirmation: PendingInvoiceConfirmation): void {
    const key = this.invoiceKey(confirmation);
    this.confirmingInvoiceKey.set(key);
    this.entryService
      .confirmInvoicePaid({ creditCardId: confirmation.creditCardId, dueDate: confirmation.dueDate })
      .subscribe({
        next: () => {
          this.confirmingInvoiceKey.set(null);
          // Recarrega tudo: a fatura confirmada sai da lista e os totais do dashboard (falta
          // pagar, já pago etc.) refletem a mudança.
          this.load();
        },
        error: () => {
          this.confirmingInvoiceKey.set(null);
        },
      });
  }
}
