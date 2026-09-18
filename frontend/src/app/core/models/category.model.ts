export type CategoryType = 'RECEITA' | 'DESPESA';

export interface Category {
  id: string;
  name: string;
  type: CategoryType;
  colorHex: string | null;
  active: boolean;
}

export interface CategoryCreateRequest {
  name: string;
  type: CategoryType;
  colorHex?: string;
}
