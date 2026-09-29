import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { EntryService } from '../../core/services/entry.service';
import { CategoryService } from '../../core/services/category.service';
import { CreditCardService } from '../../core/services/credit-card.service';
import { Entry, EntryOrigin, EntryStatus, EntryTotals, TotalsRegime } from '../../core/models/entry.model';
import { Category } from '../../core/models/category.model';
import { CreditCard } from '../../core/models/credit-card.model';
import { GroupByBankPipe } from './group-by-bank.pipe';

function firstDayOfMonth(date = new Date()): string {
  return new Date(date.getFullYear(), date.getMonth(), 1).toISOString().substring(0, 10);
}

function lastDayOfMonth(date = new Date()): string {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0).toISOString().substring(0, 10);
}

const PAGE_SIZE = 15;

const ORIGIN_LABELS: Record<EntryOrigin, string> = {
  MANUAL: 'Manual',
  RECORRENCIA: 'Recorrência',
  PARCELAMENTO: 'Parcelamento',
  IMPORTADO_BOLETO: 'Importado (boleto)',
  IMPORTADO_CARTAO: 'Importado (cartão)',
  IMPORTADO_EXTRATO: 'Importado (extrato)',
};

const STATUS_LABELS: Record<EntryStatus, string> = {
  PENDENTE: 'Pendente',
  PAGO: 'Pago',
  ATRASADO: 'Atrasado',
  CANCELADO: 'Cancelado',
};

@Component({
  selector: 'fc-entries-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, GroupByBankPipe],
  templateUrl: './entries-list.component.html',
  styleUrl: './entries-list.component.scss',
})
export class EntriesListComponent implements OnInit, OnDestroy {
  private readonly entryService = inject(EntryService);
  private readonly categoryService = inject(CategoryService);
  private readonly creditCardService = inject(CreditCardService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyed$ = new Subject<void>();

  readonly entries = signal<Entry[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly creditCards = signal<CreditCard[]>([]);
  /**
   * Bancos distintos a partir dos cartões cadastrados (dinâmico — "a lista de cartão que tiver
   * tenho que filtrar", 01/10, quinta rodada), pro filtro "por banco". value = bankName cru (o
   * que o backend compara), label = bankShortName normalizado (o que aparece na tela).
   */
  readonly bankOptions = computed(() => {
    const seen = new Map<string, string>();
    for (const card of this.creditCards()) {
      if (!seen.has(card.bankName)) seen.set(card.bankName, card.bankShortName);
    }
    return Array.from(seen.entries()).map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label));
  });
  readonly loading = signal(true);
  /** Mensagem de erro da última tentativa de carregar a lista — pra nunca ficar preso em "Carregando..." silenciosamente. */
  readonly loadError = signal<string | null>(null);
  readonly showForm = signal(false);

  readonly from = signal(firstDayOfMonth());
  readonly to = signal(lastDayOfMonth());
  readonly categoryId = signal<string>('');
  readonly status = signal<EntryStatus | ''>('');
  readonly origin = signal<EntryOrigin | ''>('');
  readonly creditCardId = signal<string>('');
  /** Filtro "por banco" (ex. "Bradesco") — mutuamente exclusivo com creditCardId (01/10, quinta rodada). */
  readonly bankName = signal<string>('');
  /** Regime do totalizador: competência (padrão, comportamento original) ou caixa (bate com a planilha do Marcio). */
  readonly regime = signal<TotalsRegime>('COMPETENCIA');
  readonly descriptionControl = new FormControl('', { nonNullable: true });

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  /**
   * Total do resultado filtrado inteiro (não só a página atual) — a pedido do Marcio (01/10):
   * os filtros de período/cartão já existiam, faltava um totalizador pra conferência (ex.:
   * bater o valor de uma fatura de cartão contra o app do banco). bankSettlements só vem
   * preenchido quando o filtro é por um único cartão.
   */
  readonly totals = signal<EntryTotals | null>(null);

  readonly selectedIds = signal<Set<string>>(new Set());
  readonly batchWorking = signal(false);
  /**
   * Linha com o menu de ações (Marcar pago/Excluir do total/Excluir) aberto — trocamos os 3
   * links lado a lado por um menu "⋮", porque 3 links sempre visíveis quebravam o layout da
   * tabela (relatado pelo Marcio, sexta rodada). null = nenhum menu aberto.
   */
  readonly openActionsMenuId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    description: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    dueDate: [new Date().toISOString().substring(0, 10), Validators.required],
    type: ['DESPESA' as 'DESPESA' | 'RECEITA', Validators.required],
    categoryId: ['', Validators.required],
  });

  ngOnInit(): void {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.creditCardService.list().subscribe((creditCards) => this.creditCards.set(creditCards));
    this.descriptionControl.valueChanges
      .pipe(debounceTime(400), distinctUntilChanged(), takeUntil(this.destroyed$))
      .subscribe(() => {
        this.page.set(0);
        this.reload();
      });
    this.reload();
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }

  /**
   * Botão "Pesquisar" manual (sexta rodada, a pedido do Marcio): os filtros já recarregam
   * sozinhos ao mudar (onFilterChange), mas isso serve de garantia visível caso algo pareça não
   * ter recarregado — clicar aqui sempre força uma busca nova com os filtros atuais.
   */
  search(): void {
    this.page.set(0);
    this.reload();
  }

  /** Abre/fecha o menu de ações (⋮) de uma linha; fecha qualquer outro que estivesse aberto. */
  toggleActionsMenu(entryId: string, event: Event): void {
    event.stopPropagation();
    this.openActionsMenuId.set(this.openActionsMenuId() === entryId ? null : entryId);
  }

  /** Fecha o menu de ações ao clicar em qualquer lugar fora dele. */
  @HostListener('document:click')
  closeActionsMenu(): void {
    this.openActionsMenuId.set(null);
  }

  reload(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.selectedIds.set(new Set());
    this.openActionsMenuId.set(null);
    const filters = {
      from: this.from(),
      to: this.to(),
      categoryId: this.categoryId() || null,
      status: (this.status() || null) as EntryStatus | null,
      origin: (this.origin() || null) as EntryOrigin | null,
      creditCardId: this.creditCardId() || null,
      bankName: this.bankName() || null,
      description: this.descriptionControl.value || null,
      regime: this.regime(),
      page: this.page(),
      size: PAGE_SIZE,
    };
    // Log deliberado (sexta rodada): o Marcio relatou que trocar filtro às vezes não recarrega a
    // tela — isso deixa rastro no console do navegador (toda chamada, sucesso ou erro) pra dar
    // pra confirmar se o reload() disparou de verdade da próxima vez que acontecer.
    console.debug('[Lançamentos] reload() disparado', filters);
    this.entryService.search(filters).subscribe({
      next: (result) => {
        console.debug('[Lançamentos] reload() concluído', { items: result.items.length, totals: result.totals });
        this.entries.set(result.items);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.totals.set(result.totals);
        this.loading.set(false);
      },
      // Sem isso, um erro HTTP (400/500) deixava a tela presa em "Carregando..." pra sempre,
      // porque loading.set(false) nunca rodava — bug relatado pelo Marcio em 01/10.
      error: (err) => {
        console.error('[Lançamentos] Falha ao carregar lançamentos', err);
        this.loading.set(false);
        this.loadError.set(
          err?.error?.message || err?.message || 'Não foi possível carregar os lançamentos. Tente novamente.',
        );
      },
    });
  }

  /** Qualquer mudança nos filtros de select/data volta pra página 0 e recarrega. */
  onFilterChange(): void {
    this.page.set(0);
    this.reload();
  }

  setFrom(value: string): void {
    this.from.set(value);
    this.onFilterChange();
  }

  setTo(value: string): void {
    this.to.set(value);
    this.onFilterChange();
  }

  setCategoryId(value: string): void {
    this.categoryId.set(value);
    this.onFilterChange();
  }

  setStatus(value: string): void {
    this.status.set(value as EntryStatus | '');
    this.onFilterChange();
  }

  setOrigin(value: string): void {
    this.origin.set(value as EntryOrigin | '');
    this.onFilterChange();
  }

  /** Selecionar um cartão específico limpa o filtro "por banco" — são mutuamente exclusivos. */
  setCreditCardId(value: string): void {
    this.creditCardId.set(value);
    if (value) this.bankName.set('');
    this.onFilterChange();
  }

  /** Filtra por todos os cartões de um banco de uma vez (ex. "Bradesco") — limpa o filtro de cartão específico. */
  setBankName(value: string): void {
    this.bankName.set(value);
    if (value) this.creditCardId.set('');
    this.onFilterChange();
  }

  setRegime(value: TotalsRegime): void {
    this.regime.set(value);
    this.onFilterChange();
  }

  clearFilters(): void {
    this.categoryId.set('');
    this.status.set('');
    this.origin.set('');
    this.creditCardId.set('');
    this.bankName.set('');
    this.descriptionControl.setValue('');
    this.page.set(0);
    this.reload();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages()) return;
    this.page.set(page);
    this.reload();
  }

  toggleForm(): void {
    this.showForm.set(!this.showForm());
  }

  submit(): void {
    if (this.form.invalid) return;
    this.entryService.create(this.form.getRawValue()).subscribe(() => {
      this.form.reset({
        description: '',
        amount: 0,
        dueDate: new Date().toISOString().substring(0, 10),
        type: 'DESPESA',
        categoryId: '',
      });
      this.showForm.set(false);
      this.reload();
    });
  }

  markAsPaid(entry: Entry): void {
    this.entryService.markAsPaid(entry.id).subscribe(() => this.reload());
  }

  markAsPending(entry: Entry): void {
    this.entryService.markAsPending(entry.id).subscribe(() => this.reload());
  }

  /** Toggle manual do "excluir do totalizador" — pra empréstimos/repasses entre pessoas que o banco não sinaliza (01/10). */
  toggleExcludedFromTotals(entry: Entry): void {
    const request = entry.excludedFromTotals
      ? this.entryService.includeInTotals(entry.id)
      : this.entryService.excludeFromTotals(entry.id);
    request.subscribe(() => this.reload());
  }

    remove(entry: Entry): void {
    this.entryService.delete(entry.id).subscribe(() => this.reload());
  }

  isSelected(entry: Entry): boolean {
    return this.selectedIds().has(entry.id);
  }

  toggleSelected(entry: Entry): void {
    const next = new Set(this.selectedIds());
    if (next.has(entry.id)) {
      next.delete(entry.id);
    } else {
      next.add(entry.id);
    }
    this.selectedIds.set(next);
  }

  allVisibleSelected(): boolean {
    const entries = this.entries();
    return entries.length > 0 && entries.every((e) => this.selectedIds().has(e.id));
  }

  toggleSelectAllVisible(): void {
    if (this.allVisibleSelected()) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(this.entries().map((e) => e.id)));
    }
  }

  batchMarkAsPaid(): void {
    const ids = Array.from(this.selectedIds());
    if (ids.length === 0) return;
    this.batchWorking.set(true);
    this.entryService.batchMarkAsPaid({ ids }).subscribe({
      next: () => {
        this.batchWorking.set(false);
        this.reload();
      },
      error: () => this.batchWorking.set(false),
    });
  }

  originLabel(origin: EntryOrigin): string {
    return ORIGIN_LABELS[origin] ?? origin;
  }

  statusLabel(status: EntryStatus): string {
    return STATUS_LABELS[status] ?? status;
  }

  readonly originOptions = Object.entries(ORIGIN_LABELS) as [EntryOrigin, string][];
  readonly statusOptions = Object.entries(STATUS_LABELS) as [EntryStatus, string][];

  batchDelete(): void {
    const ids = Array.from(this.selectedIds());
    if (ids.length === 0) return;
    this.batchWorking.set(true);
    this.entryService.batchDelete({ ids }).subscribe({
      next: () => {
        this.batchWorking.set(false);
        this.reload();
      },
      error: () => this.batchWorking.set(false),
    });
  }
}
