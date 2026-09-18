import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BoletoLinhaDigitavel } from '../models/boleto.model';

@Injectable({ providedIn: 'root' })
export class BoletoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/boletos`;

  /**
   * O backend responde 200 quando a linha é válida e 422 quando não é — em
   * ambos os casos o corpo é um BoletoLinhaDigitavel (com valid=false e uma
   * mensagem no segundo caso), então o componente trata o erro HTTP lendo
   * err.error em vez de tratar 422 como uma falha de rede.
   */
  parseLinhaDigitavel(linhaDigitavel: string): Observable<BoletoLinhaDigitavel> {
    return this.http.post<BoletoLinhaDigitavel>(`${this.baseUrl}/parse-linha-digitavel`, { linhaDigitavel });
  }
}
