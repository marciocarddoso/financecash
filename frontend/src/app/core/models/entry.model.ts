export type EntryType = 'RECEITA' | 'DESPESA';
export type EntryStatus = 'PENDENTE' | 'PAGO' | 'ATRASADO' | 'CANCELADO';
export type EntryOrigin = 'MANUAL' | 'RECORRENCIA' | 'PARCELAMENTO' | 'IMPORTADO_BOLETO' | 'IMPORTADO_CARTAO' | 'IMPORTADO_EXTRATO';
export type TotalsRegime = 'COMPETENCIA' | 'CAIXA';

export interface Entry {
  id: string;
  description: string;
  amount: number;
  dueDate: string;
  paymentDate: string | null;
  type: EntryType;
  status: EntryStatus;
  origin: EntryOrigin;
  categoryId: string;
  categoryName: string;
  accountId: string | null;
  accountName: string | null;
  creditCardId: string | null;
  creditCardName: string | null;
  creditCardBankName: string | null;
  /** Nome curto pra exibição ("Bradesco Visa") — já formatado no backend, não concatenar de novo. */
  creditCardDisplayName: string | null;
  installmentPlanId: string | null;
  installmentNumber: number | null;
  installmentsCount: number | null;
  /**
   * true quando esse lançamento não entra na soma de despesa/receita do totalizador (ver
   * EntryTotals) — ex.: transferência entre as próprias contas do usuário, ou o débito que paga
   * uma fatura de cartão já contada como despesa quando a compra foi feita. Continua aparecendo
   * normalmente na lista, só não soma no total (01/10, a pedido do Marcio).
   */
  excludedFromTotals: boolean;
}

export interface EntryCreateRequest {
  description: string;
  amount: number;
  dueDate: string;
  type: EntryType;
  categoryId: string;
  accountId?: string;
  creditCardId?: string;
}

export interface EntryBatchPayRequest {
  ids: string[];
  paymentDate?: string | null;
}

export interface EntryBatchDeleteRequest {
  ids: string[];
}

export interface EntryBatchOperationResult {
  affected: number;
  notFound: string[];
}

export interface EntrySearchParams {
  from: string;
  to: string;
  categoryId?: string | null;
  status?: EntryStatus | null;
  origin?: EntryOrigin | null;
  creditCardId?: string | null;
  /** Filtra por todos os cartões de um banco de uma vez (ex. "Bradesco") — 01/10, quinta rodada. */
  bankName?: string | null;
  description?: string | null;
  /** Regime do totalizador (não da lista, que é sempre por dueDate) — ver TotalsRegime. Sem informar, backend usa COMPETENCIA. */
  regime?: TotalsRegime | null;
  page?: number;
  size?: number;
}

/**
 * O que o banco informou como pagamento/financiamento de uma fatura (segundo a Pluggy) — usado
 * pra conferência lado a lado com o total calculado quando o filtro de Lançamentos é por um
 * único cartão (01/10, a pedido do Marcio: "o valor da fatura não está batendo com o app do
 * banco").
 */
export interface InvoiceSettlement {
  amount: number;
  transactionDate: string;
  cycleDueDate: string;
  description: string;
}

/**
 * Quebra do totalizador por cartão — a pedido do Marcio (01/10, quinta rodada): "quero ver o
 * totalizador por cartão e por banco". Agrupar essa lista por bankName (na tela) dá o "por
 * banco". Em regime de CAIXA, receita vem sempre 0 — o valor já é o líquido que o banco
 * informou pra fatura.
 */
export interface CreditCardTotal {
  creditCardId: string;
  displayName: string;
  bankName: string;
  despesa: number;
  receita: number;
}

export interface EntryTotals {
  regime: TotalsRegime;
  totalDespesa: number;
  totalReceita: number;
  net: number;
  entryCount: number;
  creditCardTotals: CreditCardTotal[];
  bankSettlements: InvoiceSettlement[];
}

export interface EntryPage {
  items: Entry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  totals: EntryTotals;
}

/**
 * Uma fatura de cartão fechada (vencimento no passado) que ainda tem compras PENDENTE —
 * o aviso "fatura tal venceu, já foi paga?" do dashboard (01/10, a pedido do Marcio: confirmação
 * manual por fatura em vez de marcar como pago sozinho, pra não esconder um caso de fatura em
 * aberto/rotativo).
 */
export interface PendingInvoiceConfirmation {
  creditCardId: string;
  creditCardName: string;
  creditCardBankName: string;
  creditCardDisplayName: string;
  dueDate: string;
  entryCount: number;
  totalAmount: number;
}

export interface ConfirmInvoicePaidRequest {
  creditCardId: string;
  dueDate: string;
  paymentDate?: string | null;
}
