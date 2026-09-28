import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PluggyConnect } from 'pluggy-connect-sdk';
import { OpenFinanceService } from '../../core/services/open-finance.service';
import { BankConnection } from '../../core/models/bank-connection.model';

/**
 * Tela "Bancos Conectados" — abre o widget "Pluggy Connect" (hospedado pela própria
 * Pluggy) para o usuário autorizar a conexão de um banco via Open Finance. Quando o
 * widget dispara onSuccess, mandamos o itemId para o backend salvar a conexão — ver
 * docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3.
 *
 * Ainda não sincroniza saldo/extrato (isso é a próxima fase, AccountSyncService /
 * TransactionImportService); por ora só mostra quais bancos já foram conectados.
 */
@Component({
  selector: 'fc-open-finance',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h1>Bancos Conectados</h1>
    <p class="fc-hint">
      Conecte suas contas via Open Finance (através da Pluggy) para, nas próximas etapas,
      trazer saldo e transações automaticamente em vez de digitar tudo manualmente.
    </p>

    <div class="fc-card fc-actions">
      <button class="fc-button" type="button" [disabled]="connecting()" (click)="conectarBanco()">
        {{ connecting() ? 'Abrindo...' : 'Conectar novo banco' }}
      </button>
      @if (errorMessage()) {
        <p class="fc-error">{{ errorMessage() }}</p>
      }
    </div>

    <div class="fc-card">
      @if (connections().length === 0) {
        <p>Nenhum banco conectado ainda.</p>
      } @else {
        <table class="fc-table">
          <thead><tr><th>Banco</th><th>Status</th><th>Conectado em</th><th>Última sincronização</th></tr></thead>
          <tbody>
            @for (connection of connections(); track connection.id) {
              <tr>
                <td>{{ connection.bankName }}</td>
                <td><span class="fc-status" [class]="'fc-status--' + connection.status.toLowerCase()">{{ statusLabel(connection.status) }}</span></td>
                <td>{{ connection.connectedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ connection.lastSyncAt ? (connection.lastSyncAt | date: 'dd/MM/yyyy HH:mm') : 'ainda não sincronizado' }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: [`
    .fc-hint { color: var(--fc-color-text-muted); font-size: 0.9rem; margin-bottom: 1.25rem; }
    .fc-actions { display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 1.5rem; }
    .fc-error { color: #b91c1c; font-size: 0.9rem; margin: 0; }
    .fc-status { padding: 0.15rem 0.55rem; border-radius: 12px; font-size: 0.8rem; font-weight: 600; }
    .fc-status--ativa { background: #dcfce7; color: #15803d; }
    .fc-status--expirada { background: #fef3c7; color: #b45309; }
    .fc-status--erro { background: #fee2e2; color: #b91c1c; }
  `],
})
export class OpenFinanceComponent implements OnInit {
  private readonly openFinanceService = inject(OpenFinanceService);

  readonly connections = signal<BankConnection[]>([]);
  readonly connecting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.openFinanceService.listConnections().subscribe((connections) => this.connections.set(connections));
  }

  statusLabel(status: BankConnection['status']): string {
    switch (status) {
      case 'ATIVA':
        return 'Ativa';
      case 'EXPIRADA':
        return 'Expirada';
      default:
        return 'Erro';
    }
  }

  conectarBanco(): void {
    this.errorMessage.set(null);
    this.connecting.set(true);

    this.openFinanceService.createConnectToken().subscribe({
      next: ({ accessToken }) => {
        const pluggyConnect = new PluggyConnect({
          connectToken: accessToken,
          includeSandbox: false,
          onSuccess: ({ item }) => {
            this.openFinanceService.saveConnection({ itemId: item.id }).subscribe({
              next: () => {
                this.connecting.set(false);
                this.reload();
              },
              error: () => {
                this.connecting.set(false);
                this.errorMessage.set('O banco foi conectado na Pluggy, mas não conseguimos salvar a conexão no FinanceCash. Tente novamente.');
              },
            });
          },
          onError: () => {
            this.connecting.set(false);
            this.errorMessage.set('Não foi possível conectar o banco. Tente novamente.');
          },
          onClose: () => {
            this.connecting.set(false);
          },
        });
        pluggyConnect.init();
      },
      error: () => {
        this.connecting.set(false);
        this.errorMessage.set('Não foi possível iniciar a conexão com a Pluggy. Tente novamente em instantes.');
      },
    });
  }
}
