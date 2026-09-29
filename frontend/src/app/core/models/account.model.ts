export type AccountType = 'CORRENTE' | 'POUPANCA' | 'INVESTIMENTO';

export interface Account {
  id: string;
  name: string;
  bankName: string;
  type: AccountType;
  investmentDescription: string | null;
  active: boolean;
  /** true = conta trazida pela sincronização Open Finance (Pluggy) — nome/banco/tipo/saldo
   * controlados pelo sync, só o active pode ser alterado pelo usuário. false = conta manual,
   * com CRUD completo. */
  syncedFromOpenFinance: boolean;
  latestBalance: number | null;
  latestBalanceDate: string | null;
}

export interface AccountCreateRequest {
  name: string;
  bankName: string;
  type: AccountType;
  investmentDescription?: string;
}

export interface AccountUpdateRequest {
  name: string;
  bankName: string;
  type: AccountType;
  investmentDescription?: string;
}

export interface BalanceSnapshotCreateRequest {
  referenceDate: string;
  balance: number;
}
