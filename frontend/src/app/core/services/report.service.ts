import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CategorySpendingReportResponse } from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/reports`;

  spendingByCategory(from: string, to: string): Observable<CategorySpendingReportResponse> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<CategorySpendingReportResponse>(`${this.baseUrl}/spending-by-category`, { params });
  }
}
