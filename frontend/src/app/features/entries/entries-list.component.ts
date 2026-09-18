import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EntryService } from '../../core/services/entry.service';
import { CategoryService } from '../../core/services/category.service';
import { Entry } from '../../core/models/entry.model';
import { Category } from '../../core/models/category.model';

function firstDayOfMonth(date = new Date()): string {
  return new Date(date.getFullYear(), date.getMonth(), 1).toISOString().substring(0, 10);
}

function lastDayOfMonth(date = new Date()): string {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0).toISOString().substring(0, 10);
}

@Component({
  selector: 'fc-entries-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './entries-list.component.html',
  styleUrl: './entries-list.component.scss',
})
export class EntriesListComponent implements OnInit {
  private readonly entryService = inject(EntryService);
  private readonly categoryService = inject(CategoryService);
  private readonly fb = inject(FormBuilder);

  readonly entries = signal<Entry[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);

  readonly from = signal(firstDayOfMonth());
  readonly to = signal(lastDayOfMonth());

  readonly selectedIds = signal<Set<string>>(new Set());
  readonly batchWorking = signal(false);

  readonly form = this.fb.nonNullable.group({
    description: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    dueDate: [new Date().toISOString().substring(0, 10), Validators.required],
    type: ['DESPESA' as 'DESPESA' | 'RECEITA', Validators.required],
    categoryId: ['', Validators.required],
  });

  ngOnInit(): void {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.selectedIds.set(new Set());
    this.entryService.listBetween(this.from(), this.to()).subscribe((entries) => {
      this.entries.set(entries);
      this.loading.set(false);
    });
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
