import { EntryType } from './entry.model';

export interface MonthClosingCategoryItem {
  categoryId: string;
  categoryName: string;
  colorHex: string | null;
  type: EntryType;
  previsto: number;
  realizado: number;
}

export interface MonthClosingResponse {
  year: number;
  month: number;
  previstoReceita: number;
  realizadoReceita: number;
  previstoDespesa: number;
  realizadoDespesa: number;
  categorias: MonthClosingCategoryItem[];
}
