export interface CreditCard {
  id: string;
  name: string;
  bankName: string;
  brand: string | null;
  /** Nome curto pra exibição ("Bradesco Visa") — já formatado no backend, usar em vez de concatenar name + bankName. */
  displayName: string;
  /** Só a parte do banco normalizada ("Bradesco") — pra rotular o filtro "por banco"; o filtro em si usa bankName (cru). */
  bankShortName: string;
  closingDay: number;
  dueDay: number;
  active: boolean;
}

export interface CreditCardCreateRequest {
  name: string;
  bankName: string;
  closingDay: number;
  dueDay: number;
}

export interface CreditCardUpdateRequest {
  name: string;
  bankName: string;
  closingDay: number;
  dueDay: number;
}
