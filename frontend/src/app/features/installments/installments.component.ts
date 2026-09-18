import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { InstallmentPlanService } from '../../core/services/installment-plan.service';
import { CategoryService } from '../../core/services/category.service';
import { AccountService } from '../../core/services/account.service';
import { CreditCardService } from '../../core/services/credit-card.service';
import { InstallmentPlan } from '../../core/models/installment-plan.model';
import { Category } from '../../core/models/category.model';
import { Account } from '../../core/models/account.model';
import { CreditCard } from '../../core/models/credit-card.model';

@Component({
  selector: 'fc-installments',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './installments.component.html',
  styleUrl: './installments.component.scss',
})
export class InstallmentsComponent implements OnInit {
  private readonly installmentPlanService = inject(InstallmentPlanService);
  private readonly categoryService = inject(CategoryService);
  private readonly accountService = inject(AccountService);
  private readonly creditCardService = inject(CreditCardService);
  private readonly fb = inject(FormBuilder);

  readonly plans = signal<InstallmentPlan[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly accounts = signal<Account[]>([]);
  readonly creditCards = signal<CreditCard[]>([]);
  readonly showForm = signal(false);

  readonly form = this.fb.nonNullable.group({
    description: ['', Validators.required],
    totalAmount: [0, [Validators.required, Validators.min(0.01)]],
    installmentsCount: [2, [Validators.required, Validators.min(2), Validators.max(96)]],
    firstDueDate: [new Date().toISOString().substring(0, 10), Validators.required],
    categoryId: ['', Validators.required],
    accountId: [''],
    creditCardId: [''],
  });

  get installmentPreview(): string {
    const total = this.form.controls.totalAmount.value;
    const count = this.form.controls.installmentsCount.value;
    if (!total || !count) return '';
    return `${count}x de ${(total / count).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })} (aprox.)`;
  }

  ngOnInit(): void {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    this.accountService.list().subscribe((accounts) => this.accounts.set(accounts));
    this.creditCardService.list().subscribe((cards) => this.creditCards.set(cards));
    this.reload();
  }

  reload(): void {
    this.installmentPlanService.list().subscribe((plans) => this.plans.set(plans));
  }

  toggleForm(): void {
    this.showForm.set(!this.showForm());
  }

  submit(): void {
    if (this.form.invalid) return;
    const raw = this.form.getRawValue();

    this.installmentPlanService
      .create({
        description: raw.description,
        totalAmount: raw.totalAmount,
        installmentsCount: raw.installmentsCount,
        firstDueDate: raw.firstDueDate,
        categoryId: raw.categoryId,
        accountId: raw.accountId || undefined,
        creditCardId: raw.creditCardId || undefined,
      })
      .subscribe(() => {
        this.form.reset({
          description: '', totalAmount: 0, installmentsCount: 2,
          firstDueDate: new Date().toISOString().substring(0, 10), categoryId: '', accountId: '', creditCardId: '',
        });
        this.showForm.set(false);
        this.reload();
      });
  }
}
