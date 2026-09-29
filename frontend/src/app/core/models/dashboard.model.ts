import { Entry } from './entry.model';

export interface DashboardResponse {
  referenceDate: string;
  dueToday: Entry[];
  overdue: Entry[];
  /** Despesas com vencimento hoje, ainda não pagas. */
  totalPendingToday: number;
  /** Despesas do mês que ainda faltam pagar (não inclui as já pagas). */
  totalPendingMonth: number;
  /** Despesas do mês já pagas. */
  totalPaidMonth: number;
  /** Total geral de despesas do mês: totalPendingMonth + totalPaidMonth. */
  totalExpensesMonth: number;
  /** Receita do mês já recebida (já refletida em consolidatedBalance). */
  totalIncomePaidMonth: number;
  /** Receita do mês prevista, ainda não recebida. */
  totalIncomePendingMonth: number;
  /** Soma do saldo mais recente de cada conta ativa (saldo real, vindo dos bancos). */
  consolidatedBalance: number;
  /** consolidatedBalance + totalIncomePendingMonth - totalPendingMonth. */
  projectedBalanceEndOfMonth: number;
}
