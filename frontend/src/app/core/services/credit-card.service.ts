import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreditCard, CreditCardCreateRequest, CreditCardUpdateRequest } from '../models/credit-card.model';

@Injectable({ providedIn: 'root' })
export class CreditCardService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/credit-cards`;

  list(): Observable<CreditCard[]> {
    return this.http.get<CreditCard[]>(this.baseUrl);
  }

  create(request: CreditCardCreateRequest): Observable<CreditCard> {
    return this.http.post<CreditCard>(this.baseUrl, request);
  }

  update(id: string, request: CreditCardUpdateRequest): Observable<CreditCard> {
    return this.http.put<CreditCard>(`${this.baseUrl}/${id}`, request);
  }

  deactivate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
