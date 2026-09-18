import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MonthClosingResponse } from '../models/month-closing.model';

@Injectable({ providedIn: 'root' })
export class MonthClosingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/month-closing`;

  get(year: number, month: number): Observable<MonthClosingResponse> {
    const params = new HttpParams().set('year', year).set('month', month);
    return this.http.get<MonthClosingResponse>(this.baseUrl, { params });
  }
}
