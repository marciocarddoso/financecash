export interface BoletoParseRequest {
  linhaDigitavel: string;
}

export interface BoletoLinhaDigitavel {
  valid: boolean;
  bankCode: string | null;
  bankName: string | null;
  amount: number | null;
  dueDate: string | null;
  fieldChecksumsOk: boolean;
  message: string | null;
}
