export interface InstallmentPlan {
  id: string;
  description: string;
  totalAmount: number;
  installmentsCount: number;
  firstDueDate: string;
  categoryId: string;
  categoryName: string;
}

export interface InstallmentPlanCreateRequest {
  description: string;
  totalAmount: number;
  installmentsCount: number;
  firstDueDate: string;
  categoryId: string;
  accountId?: string;
  creditCardId?: string;
}
