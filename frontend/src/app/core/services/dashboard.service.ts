import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardResponse } from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/dashboard`;

  get(referenceDate?: string): Observable<DashboardResponse> {
    const params = referenceDate ? new HttpParams().set('referenceDate', referenceDate) : undefined;
    return this.http.get<DashboardResponse>(this.baseUrl, { params });
  }
}
