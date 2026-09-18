import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InstallmentPlan, InstallmentPlanCreateRequest } from '../models/installment-plan.model';

@Injectable({ providedIn: 'root' })
export class InstallmentPlanService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/installment-plans`;

  list(): Observable<InstallmentPlan[]> {
    return this.http.get<InstallmentPlan[]>(this.baseUrl);
  }

  /** Cria a compra parcelada — o backend já gera todos os lançamentos das parcelas futuras. */
  create(request: InstallmentPlanCreateRequest): Observable<InstallmentPlan> {
    return this.http.post<InstallmentPlan>(this.baseUrl, request);
  }
}
