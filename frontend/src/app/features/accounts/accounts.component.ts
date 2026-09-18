import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from '../../core/services/account.service';
import { Account } from '../../core/models/account.model';

@Component({
  selector: 'fk-accounts',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  template: `
    <h1>Contas &amp; Saldos</h1>
    <p class="fk-hint">
      Cadastre suas contas (bancos e aplicações em CDI) e registre o saldo real sempre que conferir o extrato —
      o dashboard usa o último saldo informado de cada conta para consolidar sua posição financeira.
    </p>

    <form class="fk-card fk-inline-form" [formGroup]="form" (ngSubmit)="submit()">
      <input type="text" placeholder="Nome (ex.: Conta Corrente)" formControlName="name" />
      <input type="text" placeholder="Banco (ex.: Nubank)" formControlName="bankName" />
      <select formControlName="type">
        <option value="CORRENTE">Corrente</option>
        <option value="POUPANCA">Poupança</option>
        <option value="INVESTIMENTO">Investimento</option>
      </select>
      <button class="fk-button" type="submit" [disabled]="form.invalid">Adicionar conta</button>
    </form>

    <div class="fk-card">
      @if (accounts().length === 0) {
        <p>Nenhuma conta cadastrada ainda.</p>
      } @else {
        <table class="fk-table">
          <thead><tr><th>Nome</th><th>Banco</th><th>Tipo</th><th>Último saldo</th><th>Atualizar saldo</th></tr></thead>
          <tbody>
            @for (account of accounts(); track account.id) {
              <tr>
                <td>{{ account.name }}</td>
                <td>{{ account.bankName }}</td>
                <td>{{ account.type }}</td>
                <td>
                  {{ account.latestBalance !== null ? (account.latestBalance | currency: 'BRL') : '—' }}
                  @if (account.latestBalanceDate) {
                    <span class="fk-hint"> ({{ account.latestBalanceDate }})</span>
                  }
                </td>
                <td>
                  <input type="number" step="0.01" placeholder="Novo saldo" [(ngModel)]="newBalances[account.id]" [ngModelOptions]="{standalone: true}" />
                  <button class="fk-link" (click)="registerBalance(account)">Salvar</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: [`
    .fk-hint { color: var(--fk-color-text-muted); font-size: 0.9rem; margin-bottom: 1.25rem; }
    .fk-inline-form { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.5rem; }
    .fk-inline-form input[type="text"] { flex: 1; padding: 0.45rem 0.6rem; border: 1px solid var(--fk-color-border); border-radius: 6px; }
    .fk-inline-form select { padding: 0.45rem 0.6rem; border: 1px solid var(--fk-color-border); border-radius: 6px; }
    td input[type="number"] { width: 110px; padding: 0.3rem 0.5rem; border: 1px solid var(--fk-color-border); border-radius: 6px; margin-right: 0.5rem; }
    .fk-link { background: none; border: none; color: var(--fk-color-primary); cursor: pointer; }
  `],
})
export class AccountsComponent implements OnInit {
  private readonly accountService = inject(AccountService);
  private readonly fb = inject(FormBuilder);

  readonly accounts = signal<Account[]>([]);
  readonly newBalances: Record<string, number> = {};

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    bankName: ['', Validators.required],
    type: ['CORRENTE' as 'CORRENTE' | 'POUPANCA' | 'INVESTIMENTO', Validators.required],
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
  }

  submit(): void {
    if (this.form.invalid) return;
    this.accountService.create(this.form.getRawValue()).subscribe(() => {
      this.form.reset({ name: '', bankName: '', type: 'CORRENTE' });
      this.reload();
    });
  }

  registerBalance(account: Account): void {
    const balance = this.newBalances[account.id];
    if (balance === undefined || balance === null) return;

    this.accountService
      .registerBalance(account.id, { referenceDate: new Date().toISOString().substring(0, 10), balance })
      .subscribe(() => {
        delete this.newBalances[account.id];
        this.reload();
      });
  }
}
