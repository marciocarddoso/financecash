export interface CategorySpendingReportItem {
  categoryId: string;
  categoryName: string;
  colorHex: string | null;
  total: number;
  entryCount: number;
  percentageOfTotal: number;
}

export interface CategorySpendingReportResponse {
  from: string;
  to: string;
  totalSpent: number;
  items: CategorySpendingReportItem[];
}
