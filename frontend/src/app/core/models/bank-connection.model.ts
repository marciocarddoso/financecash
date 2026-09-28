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
