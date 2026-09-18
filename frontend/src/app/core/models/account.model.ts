export type AccountType = 'CORRENTE' | 'POUPANCA' | 'INVESTIMENTO';

export interface Account {
  id: string;
  name: string;
  bankName: string;
  type: AccountType;
  investmentDescription: string | null;
  active: boolean;
  latestBalance: number | null;
  latestBalanceDate: string | null;
}

export interface AccountCreateRequest {
  name: string;
  bankName: string;
  type: AccountType;
  investmentDescription?: string;
}

export interface BalanceSnapshotCreateRequest {
  referenceDate: string;
  balance: number;
}
