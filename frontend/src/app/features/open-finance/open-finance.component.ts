import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PluggyConnect } from 'pluggy-connect-sdk';
import { OpenFinanceService } from '../../core/services/open-finance.service';
import { BankAccountInfo, BankConnection } from '../../core/models/bank-connection.model';

/**
 * Tela "Bancos Conectados" — abre o widget "Pluggy Connect" (hospedado pela própria
 * Pluggy) para o usuário autorizar a conexão de um banco via Open Finance. Quando o
 * widget dispara onSuccess, mandamos o itemId para o backend salvar a conexão — ver
 * docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3.
 *
 * O nome salvo por conexão é o do conector usado (ex.: "MeuPluggy", que agrega várias
 * contas já conectadas em meu.pluggy.ai) — não é o banco individual. Pra ver qual banco
 * de fato está por trás de cada conexão, "Ver contas" busca ao vivo na Pluggy
 * (PluggyClient.listAccounts) e mostra nome/número/saldo de cada conta daquele item.
 *
 * Ainda não sincroniza isso pro FinanceCash de fato (isso é a próxima fase,
 * AccountSyncService / TransactionImportService) — aqui é só consulta/exibição.
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
          <thead><tr><th>Banco</th><th>Status</th><th>Conectado em</th><th>Última sincronização</th><th></th></tr></thead>
          <tbody>
            @for (connection of connections(); track connection.id) {
              <tr>
                <td>{{ connection.bankName }}</td>
                <td><span class="fc-status" [class]="'fc-status--' + connection.status.toLowerCase()">{{ statusLabel(connection.status) }}</span></td>
                <td>{{ connection.connectedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ connection.lastSyncAt ? (connection.lastSyncAt | date: 'dd/MM/yyyy HH:mm') : 'ainda não sincronizado' }}</td>
                <td>
                  <button class="fc-link" (click)="toggleAccounts(connection)">
                    {{ isExpanded(connection.id) ? 'Ocultar contas' : 'Ver contas' }}
                  </button>
                </td>
              </tr>
              @if (isExpanded(connection.id)) {
                <tr class="fc-detail-row">
                  <td colspan="5">
                    @if (loadingAccountsId() === connection.id) {
                      <p class="fc-hint">Carregando contas...</p>
                    } @else if ((accountsByConnection()[connection.id] ?? []).length === 0) {
                      <p class="fc-hint">Nenhuma conta encontrada pra essa conexão.</p>
                    } @else {
                      <table class="fc-table fc-table--nested">
                        <thead><tr><th>Conta</th><th>Tipo</th><th>Número</th><th>Saldo</th></tr></thead>
                        <tbody>
                          @for (account of accountsByConnection()[connection.id]; track account.id) {
                            <tr>
                              <td>{{ account.marketingName || account.name }}</td>
                              <td>{{ account.type }}{{ account.subtype ? ' / ' + account.subtype : '' }}</td>
                              <td>{{ account.number || '—' }}</td>
                              <td>{{ account.balance !== null ? (account.balance | currency: (account.currencyCode || 'BRL')) : '—' }}</td>
                            </tr>
                          }
                        </tbody>
                      </table>
                    }
                  </td>
                </tr>
              }
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
    .fc-detail-row td { background: var(--fc-color-bg-muted, #f8fafc); padding: 0.75rem 1rem; }
    .fc-table--nested { margin: 0; }
  `],
})
export class OpenFinanceComponent implements OnInit {
  private readonly openFinanceService = inject(OpenFinanceService);

  readonly connections = signal<BankConnection[]>([]);
  readonly connecting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly expandedIds = signal<Set<string>>(new Set());
  readonly loadingAccountsId = signal<string | null>(null);
  readonly accountsByConnection = signal<Record<string, BankAccountInfo[]>>({});

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

  isExpanded(connectionId: string): boolean {
    return this.expandedIds().has(connectionId);
  }

  toggleAccounts(connection: BankConnection): void {
    const expanded = new Set(this.expandedIds());
    if (expanded.has(connection.id)) {
      expanded.delete(connection.id);
      this.expandedIds.set(expanded);
      return;
    }
    expanded.add(connection.id);
    this.expandedIds.set(expanded);
    if (this.accountsByConnection()[connection.id]) {
      return;
    }
    this.loadingAccountsId.set(connection.id);
    this.openFinanceService.listAccounts(connection.id).subscribe({
      next: (accounts) => {
        this.accountsByConnection.set({ ...this.accountsByConnection(), [connection.id]: accounts });
        this.loadingAccountsId.set(null);
      },
      error: () => {
        this.accountsByConnection.set({ ...this.accountsByConnection(), [connection.id]: [] });
        this.loadingAccountsId.set(null);
      },
    });
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
