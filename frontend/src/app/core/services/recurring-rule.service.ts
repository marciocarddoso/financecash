import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RecurringRule, RecurringRuleCreateRequest } from '../models/recurring-rule.model';

@Injectable({ providedIn: 'root' })
export class RecurringRuleService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/recurring-rules`;

  list(): Observable<RecurringRule[]> {
    return this.http.get<RecurringRule[]>(this.baseUrl);
  }

  create(request: RecurringRuleCreateRequest): Observable<RecurringRule> {
    return this.http.post<RecurringRule>(this.baseUrl, request);
  }

  deactivate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Dispara o provisionamento: gera os lançamentos futuros de todas as regras ativas. */
  generate(monthsAhead = 12): Observable<{ lancamentosGerados: number }> {
    const params = new HttpParams().set('monthsAhead', monthsAhead);
    return this.http.post<{ lancamentosGerados: number }>(`${this.baseUrl}/generate`, null, { params });
  }
}
