export interface EntryImportRowError {
  line: number;
  message: string;
}

export interface EntryImportSummary {
  imported: number;
  duplicates: number;
  errors: EntryImportRowError[];
}
