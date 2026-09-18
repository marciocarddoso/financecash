import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RecurringRuleService } from '../../core/services/recurring-rule.service';
import { CategoryService } from '../../core/services/category.service';
import { RecurrenceFrequency, RecurringRule } from '../../core/models/recurring-rule.model';
import { Category } from '../../core/models/category.model';

const MONTH_NAMES = [
  'Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez',
];

@Component({
  selector: 'fc-recurring-rules',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './recurring-rules.component.html',
  styleUrl: './recurring-rules.component.scss',
})
export class RecurringRulesComponent implements OnInit {
  private readonly recurringRuleService = inject(RecurringRuleService);
  private readonly categoryService = inject(CategoryService);
  private readonly fb = inject(FormBuilder);

  readonly rules = signal<RecurringRule[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly showForm = signal(false);
  readonly generating = signal(false);
  readonly generateMessage = signal<string | null>(null);

  readonly months = MONTH_NAMES.map((label, index) => ({ label, value: index + 1 }));

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    type: ['DESPESA' as 'DESPESA' | 'RECEITA', Validators.required],
    categoryId: ['', Validators.required],
    frequency: ['MENSAL' as RecurrenceFrequency, Validators.required],
    dayOfMonth: [5, [Validators.required, Validators.min(1), Validators.max(31)]],
    referenceMonths: this.fb.nonNullable.control<number[]>([]),
    startDate: [new Date().toISOString().substring(0, 10), Validators.required],
    initialAmount: [0, [Validators.required, Validators.min(0.01)]],
  });

  ngOnInit(): void {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.reload();
  }

  reload(): void {
    this.recurringRuleService.list().subscribe((rules) => this.rules.set(rules));
  }

  get needsReferenceMonths(): boolean {
    const frequency = this.form.controls.frequency.value;
    return frequency === 'SEMESTRAL' || frequency === 'ANUAL';
  }

  toggleForm(): void {
    this.showForm.set(!this.showForm());
  }

  toggleMonth(month: number, checked: boolean): void {
    const current = this.form.controls.referenceMonths.value;
    const next = checked ? [...current, month] : current.filter((m) => m !== month);
    this.form.controls.referenceMonths.setValue(next.sort((a, b) => a - b));
  }

  isMonthSelected(month: number): boolean {
    return this.form.controls.referenceMonths.value.includes(month);
  }

  submit(): void {
    if (this.form.invalid) return;
    this.recurringRuleService.create(this.form.getRawValue()).subscribe(() => {
      this.form.reset({
        name: '', type: 'DESPESA', categoryId: '', frequency: 'MENSAL',
        dayOfMonth: 5, referenceMonths: [], startDate: new Date().toISOString().substring(0, 10), initialAmount: 0,
      });
      this.showForm.set(false);
      this.reload();
    });
  }

  deactivate(rule: RecurringRule): void {
    this.recurringRuleService.deactivate(rule.id).subscribe(() => this.reload());
  }

  generate(): void {
    this.generating.set(true);
    this.generateMessage.set(null);
    this.recurringRuleService.generate(12).subscribe({
      next: (result) => {
        this.generateMessage.set(`${result.lancamentosGerados} lançamento(s) gerado(s) para os próximos 12 meses.`);
        this.generating.set(false);
      },
      error: () => {
        this.generateMessage.set('Não foi possível gerar os lançamentos agora.');
        this.generating.set(false);
      },
    });
  }
}
