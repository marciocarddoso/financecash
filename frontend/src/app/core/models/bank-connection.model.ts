export type BankConnectionStatus = 'ATIVA' | 'EXPIRADA' | 'ERRO';

export interface BankConnection {
  id: string;
  bankName: string;
  status: BankConnectionStatus;
  connectedAt: string;
  lastSyncAt: string | null;
}

export interface ConnectTokenResponse {
  accessToken: string;
}

export interface BankConnectionCreateRequest {
  itemId: string;
}

export interface BankAccountInfo {
  id: string;
  type: string;
  subtype: string | null;
  number: string | null;
  name: string;
  marketingName: string | null;
  balance: number | null;
  currencyCode: string | null;
}

export interface SyncResult {
  accountsCreated: number;
  accountsUpdated: number;
  creditCardsSkipped: number;
}
