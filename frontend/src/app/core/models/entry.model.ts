export type EntryType = 'RECEITA' | 'DESPESA';
export type EntryStatus = 'PENDENTE' | 'PAGO' | 'ATRASADO' | 'CANCELADO';
export type EntryOrigin = 'MANUAL' | 'RECORRENCIA' | 'PARCELAMENTO' | 'IMPORTADO_BOLETO' | 'IMPORTADO_CARTAO';

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
  installmentPlanId: string | null;
  installmentNumber: number | null;
  installmentsCount: number | null;
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
