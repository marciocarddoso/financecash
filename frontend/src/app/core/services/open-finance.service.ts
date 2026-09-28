import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BankConnection, BankConnectionCreateRequest, ConnectTokenResponse } from '../models/bank-connection.model';

@Injectable({ providedIn: 'root' })
export class OpenFinanceService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/openfinance`;

  createConnectToken(): Observable<ConnectTokenResponse> {
    return this.http.post<ConnectTokenResponse>(`${this.baseUrl}/connect-token`, {});
  }

  listConnections(): Observable<BankConnection[]> {
    return this.http.get<BankConnection[]>(`${this.baseUrl}/connections`);
  }

  saveConnection(request: BankConnectionCreateRequest): Observable<BankConnection> {
    return this.http.post<BankConnection>(`${this.baseUrl}/connections`, request);
  }
}
