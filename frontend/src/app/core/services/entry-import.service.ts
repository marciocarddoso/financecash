import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EntryImportSummary } from '../models/entry-import.model';

@Injectable({ providedIn: 'root' })
export class EntryImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/entries`;

  importCsv(file: File): Observable<EntryImportSummary> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<EntryImportSummary>(`${this.baseUrl}/import-csv`, formData);
  }
}
