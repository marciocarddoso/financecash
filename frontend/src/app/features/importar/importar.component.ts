import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { EntryImportService } from '../../core/services/entry-import.service';
import { EntryImportSummary } from '../../core/models/entry-import.model';
import { BoletoService } from '../../core/services/boleto.service';
import { BoletoLinhaDigitavel } from '../../core/models/boleto.model';
import { EntryService } from '../../core/services/entry.service';
import { CategoryService } from '../../core/services/category.service';
import { Category } from '../../core/models/category.model';

@Component({
  selector: 'fc-importar',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <h1>Importar lançamentos</h1>

    <section class="fc-card">
      <h2>Importar planilha (CSV)</h2>
      <p class="fc-hint">
        Gere o CSV a partir da sua planilha com
        <code>scripts/import_numbers_to_csv.py</code> (uma vez por banco:
        Bradesco, Nubank, C6 Bank) e envie o arquivo aqui. Reenviar o mesmo
        arquivo não duplica lançamentos — linhas já importadas são puladas
        automaticamente.
      </p>

      <div class="fc-upload-row">
        <input type="file" accept=".csv" (change)="onFileSelected($event)" />
        <button class="fc-button" [disabled]="!selectedFile() || importing()" (click)="importCsv()">
          {{ importing() ? 'Importando...' : 'Importar CSV' }}
        </button>
      </div>

      @if (importSummary(); as summary) {
        <div class="fc-summary">
          <p>
            <strong>{{ summary.imported }}</strong> lançamentos importados,
            <strong>{{ summary.duplicates }}</strong> já existiam (puladas),
            <strong>{{ summary.errors.length }}</strong> com erro.
          </p>
          @if (summary.errors.length > 0) {
            <table class="fc-table">
              <thead><tr><th>Linha</th><th>Motivo</th></tr></thead>
              <tbody>
                @for (error of summary.errors; track error.line) {
                  <tr><td>{{ error.line }}</td><td>{{ error.message }}</td></tr>
                }
              </tbody>
            </table>
          }
        </div>
      }
    </section>

    <section class="fc-card">
      <h2>Colar linha digitável de um boleto</h2>
      <p class="fc-hint">
        Cole a sequência de números do boleto (com ou sem pontos/espaços) para
        pré-preencher o lançamento com banco, valor e vencimento.
      </p>

      <div class="fc-upload-row">
        <input
          type="text"
          placeholder="00190.00009 00000.000000 00000.000000 0 13810000000000"
          [formControl]="linhaDigitavelControl"
        />
        <button class="fc-button" [disabled]="!linhaDigitavelControl.value || parsing()" (click)="parseLinha()">
          {{ parsing() ? 'Lendo...' : 'Ler boleto' }}
        </button>
      </div>

      @if (boletoResult(); as result) {
        @if (!result.valid) {
          <p class="fc-error">{{ result.message }}</p>
        } @else {
          <form class="fc-boleto-form" [formGroup]="entryForm" (ngSubmit)="lancarBoleto()">
            <div class="fc-boleto-form__row">
              <label>
                Banco
                <input type="text" [value]="result.bankName" disabled />
              </label>
              <label>
                Descrição
                <input type="text" formControlName="description" />
              </label>
            </div>
            <div class="fc-boleto-form__row">
              <label>
                Valor
                <input type="number" step="0.01" formControlName="amount" />
              </label>
              <label>
                Vencimento
                <input type="date" formControlName="dueDate" />
              </label>
              <label>
                Categoria
                <select formControlName="categoryId">
                  <option value="" disabled>Selecione...</option>
                  @for (category of categories(); track category.id) {
                    <option [value]="category.id">{{ category.name }}</option>
                  }
                </select>
              </label>
            </div>
            <button class="fc-button" type="submit" [disabled]="entryForm.invalid || launching()">
              {{ launching() ? 'Lançando...' : 'Lançar' }}
            </button>
            @if (launched()) {
              <p class="fc-success">Lançamento criado com sucesso.</p>
            }
          </form>
        }
      }
    </section>
  `,
  styles: [`
    .fc-hint { color: var(--fc-color-text-muted, #666); margin: 0 0 1rem; }
    .fc-upload-row { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1rem; }
    .fc-upload-row input[type="text"] { flex: 1; padding: 0.45rem 0.6rem; border: 1px solid var(--fc-color-border); border-radius: 6px; font-family: monospace; }
    .fc-summary { margin-top: 1rem; }
    .fc-error { color: var(--fc-color-danger); }
    .fc-success { color: var(--fc-color-success, #1e6f5c); }
    .fc-boleto-form__row { display: flex; gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .fc-boleto-form__row label { display: flex; flex-direction: column; gap: 0.3rem; font-size: 0.85rem; flex: 1; min-width: 160px; }
    .fc-boleto-form__row input, .fc-boleto-form__row select { padding: 0.45rem 0.6rem; border: 1px solid var(--fc-color-border); border-radius: 6px; }
    section.fc-card + section.fc-card { margin-top: 1.5rem; }
  `],
})
export class ImportarComponent implements OnInit {
  private readonly entryImportService = inject(EntryImportService);
  private readonly boletoService = inject(BoletoService);
  private readonly entryService = inject(EntryService);
  private readonly categoryService = inject(CategoryService);
  private readonly fb = inject(FormBuilder);

  readonly categories = signal<Category[]>([]);

  // --- CSV ---
  readonly selectedFile = signal<File | null>(null);
  readonly importing = signal(false);
  readonly importSummary = signal<EntryImportSummary | null>(null);

  // --- Linha digitável ---
  readonly linhaDigitavelControl = this.fb.nonNullable.control('');
  readonly parsing = signal(false);
  readonly boletoResult = signal<BoletoLinhaDigitavel | null>(null);
  readonly launching = signal(false);
  readonly launched = signal(false);

  readonly entryForm = this.fb.nonNullable.group({
    description: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    dueDate: ['', Validators.required],
    categoryId: ['', Validators.required],
  });

  ngOnInit(): void {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
    this.importSummary.set(null);
  }

  importCsv(): void {
    const file = this.selectedFile();
    if (!file) return;
    this.importing.set(true);
    this.entryImportService.importCsv(file).subscribe({
      next: (summary) => {
        this.importSummary.set(summary);
        this.importing.set(false);
      },
      error: () => this.importing.set(false),
    });
  }

  parseLinha(): void {
    const linha = this.linhaDigitavelControl.value;
    if (!linha) return;
    this.parsing.set(true);
    this.launched.set(false);
    this.boletoService
      .parseLinhaDigitavel(linha)
      .pipe(catchError((err) => of(err.error as BoletoLinhaDigitavel)))
      .subscribe((result) => {
        this.boletoResult.set(result);
        this.parsing.set(false);
        if (result?.valid) {
          this.entryForm.patchValue({
            description: `Boleto ${result.bankName ?? ''}`.trim(),
            amount: result.amount ?? 0,
            dueDate: result.dueDate ?? '',
          });
        }
      });
  }

  lancarBoleto(): void {
    if (this.entryForm.invalid) return;
    const value = this.entryForm.getRawValue();
    this.launching.set(true);
    this.entryService
      .create({
        description: value.description,
        amount: value.amount,
        dueDate: value.dueDate,
        type: 'DESPESA',
        categoryId: value.categoryId,
      })
      .subscribe({
        next: () => {
          this.launching.set(false);
          this.launched.set(true);
          this.linhaDigitavelControl.setValue('');
          this.boletoResult.set(null);
        },
        error: () => this.launching.set(false),
      });
  }
}
