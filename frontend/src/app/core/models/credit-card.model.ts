export interface CreditCard {
  id: string;
  name: string;
  bankName: string;
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
