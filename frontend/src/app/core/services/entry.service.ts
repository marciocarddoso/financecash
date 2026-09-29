import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ConfirmInvoicePaidRequest,
  Entry,
  EntryBatchDeleteRequest,
  EntryBatchOperationResult,
  EntryBatchPayRequest,
  EntryCreateRequest,
  EntryPage,
  EntrySearchParams,
  PendingInvoiceConfirmation,
} from '../models/entry.model';

@Injectable({ providedIn: 'root' })
export class EntryService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/entries`;

  listBetween(from: string, to: string): Observable<Entry[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<Entry[]>(this.baseUrl, { params });
  }

  search(filters: EntrySearchParams): Observable<EntryPage> {
    let params = new HttpParams().set('from', filters.from).set('to', filters.to);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.origin) params = params.set('origin', filters.origin);
    if (filters.creditCardId) params = params.set('creditCardId', filters.creditCardId);
    if (filters.bankName) params = params.set('bankName', filters.bankName);
    if (filters.description) params = params.set('description', filters.description);
    if (filters.regime) params = params.set('regime', filters.regime);
    params = params.set('page', String(filters.page ?? 0)).set('size', String(filters.size ?? 50));
    return this.http.get<EntryPage>(`${this.baseUrl}/search`, { params });
  }

  create(request: EntryCreateRequest): Observable<Entry> {
    return this.http.post<Entry>(this.baseUrl, request);
  }

  markAsPaid(id: string, paymentDate?: string): Observable<Entry> {
    const params = paymentDate ? new HttpParams().set('paymentDate', paymentDate) : undefined;
    return this.http.patch<Entry>(`${this.baseUrl}/${id}/pay`, null, { params });
  }

  markAsPending(id: string): Observable<Entry> {
    return this.http.patch<Entry>(`${this.baseUrl}/${id}/unpay`, null);
  }

  excludeFromTotals(id: string): Observable<Entry> {
    return this.http.patch<Entry>(`${this.baseUrl}/${id}/exclude-from-totals`, null);
  }

  includeInTotals(id: string): Observable<Entry> {
    return this.http.patch<Entry>(`${this.baseUrl}/${id}/include-in-totals`, null);
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

  pendingInvoiceConfirmations(referenceDate?: string): Observable<PendingInvoiceConfirmation[]> {
    const params = referenceDate ? new HttpParams().set('referenceDate', referenceDate) : undefined;
    return this.http.get<PendingInvoiceConfirmation[]>(`${this.baseUrl}/pending-invoice-confirmations`, { params });
  }

  confirmInvoicePaid(request: ConfirmInvoicePaidRequest): Observable<EntryBatchOperationResult> {
    return this.http.post<EntryBatchOperationResult>(`${this.baseUrl}/confirm-invoice-paid`, request);
  }
}
