import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Account, AccountCreateRequest, BalanceSnapshotCreateRequest } from '../models/account.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/accounts`;

  list(): Observable<Account[]> {
    return this.http.get<Account[]>(this.baseUrl);
  }

  create(request: AccountCreateRequest): Observable<Account> {
    return this.http.post<Account>(this.baseUrl, request);
  }

  registerBalance(accountId: string, request: BalanceSnapshotCreateRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${accountId}/balance-snapshots`, request);
  }
}
