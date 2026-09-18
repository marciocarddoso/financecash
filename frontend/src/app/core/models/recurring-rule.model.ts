import { EntryType } from './entry.model';

export type RecurrenceFrequency = 'MENSAL' | 'BIMESTRAL' | 'TRIMESTRAL' | 'SEMESTRAL' | 'ANUAL';

export interface RecurringRule {
  id: string;
  name: string;
  type: EntryType;
  categoryId: string;
  categoryName: string;
  frequency: RecurrenceFrequency;
  dayOfMonth: number;
  referenceMonths: number[];
  startDate: string;
  endDate: string | null;
  active: boolean;
  currentAmount: number;
}

export interface RecurringRuleCreateRequest {
  name: string;
  type: EntryType;
  categoryId: string;
  accountId?: string;
  frequency: RecurrenceFrequency;
  dayOfMonth: number;
  referenceMonths?: number[];
  startDate: string;
  endDate?: string;
  initialAmount: number;
}
