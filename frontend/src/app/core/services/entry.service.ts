import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Entry,
  EntryBatchDeleteRequest,
  EntryBatchOperationResult,
  EntryBatchPayRequest,
  EntryCreateRequest,
} from '../models/entry.model';

@Injectable({ providedIn: 'root' })
export class EntryService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/entries`;

  listBetween(from: string, to: string): Observable<Entry[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<Entry[]>(this.baseUrl, { params });
  }

  create(request: EntryCreateRequest): Observable<Entry> {
    return this.http.post<Entry>(this.baseUrl, request);
  }

  markAsPaid(id: string, paymentDate?: string): Observable<Entry> {
    const params = paymentDate ? new HttpParams().set('paymentDate', paymentDate) : undefined;
    return this.http.patch<Entry>(`${this.baseUrl}/${id}/pay`, null, { params });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  batchMarkAsPaid(request: EntryBatchPayRequest): Observable<EntryBatchOperationResult> {
    return this.http.patch<EntryBatchOperationResult>(`${this.baseUrl}/batch-pay`, request);
  }

  batchDelete(request: EntryBatchDeleteRequest): Observable<EntryBatchOperationResult> {
    return this.http.post<EntryBatchOperationResult>(`${this.baseUrl}/batch-delete`, request);
  }
}
