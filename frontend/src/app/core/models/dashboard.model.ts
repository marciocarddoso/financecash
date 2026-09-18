import { Entry } from './entry.model';

export interface DashboardResponse {
  referenceDate: string;
  dueToday: Entry[];
  overdue: Entry[];
  totalPendingToday: number;
  totalPendingMonth: number;
  totalPaidMonth: number;
  consolidatedBalance: number;
  projectedBalanceEndOfMonth: number;
}
