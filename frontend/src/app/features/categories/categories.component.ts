import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CategoryService } from '../../core/services/category.service';
import { Category } from '../../core/models/category.model';

@Component({
  selector: 'fk-categories',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <h1>Categorias</h1>

    <form class="fk-card fk-inline-form" [formGroup]="form" (ngSubmit)="submit()">
      <input type="text" placeholder="Nome (ex.: Mercado)" formControlName="name" />
      <select formControlName="type">
        <option value="DESPESA">Despesa</option>
        <option value="RECEITA">Receita</option>
      </select>
      <input type="color" formControlName="colorHex" />
      <button class="fk-button" type="submit" [disabled]="form.invalid">Adicionar</button>
    </form>

    <div class="fk-card">
      @if (categories().length === 0) {
        <p>Nenhuma categoria cadastrada ainda.</p>
      } @else {
        <table class="fk-table">
          <thead><tr><th>Nome</th><th>Tipo</th><th>Cor</th><th></th></tr></thead>
          <tbody>
            @for (category of categories(); track category.id) {
              <tr>
                <td>{{ category.name }}</td>
                <td>{{ category.type }}</td>
                <td><span class="fk-color-dot" [style.background]="category.colorHex ?? '#ccc'"></span></td>
                <td><button class="fk-link fk-link--danger" (click)="remove(category)">Remover</button></td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: [`
    .fk-inline-form { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.5rem; }
    .fk-inline-form input[type="text"] { flex: 1; padding: 0.45rem 0.6rem; border: 1px solid var(--fk-color-border); border-radius: 6px; }
    .fk-inline-form select { padding: 0.45rem 0.6rem; border: 1px solid var(--fk-color-border); border-radius: 6px; }
    .fk-color-dot { display: inline-block; width: 14px; height: 14px; border-radius: 50%; }
    .fk-link { background: none; border: none; color: var(--fk-color-primary); cursor: pointer; }
    .fk-link--danger { color: var(--fk-color-danger); }
  `],
})
export class CategoriesComponent implements OnInit {
  private readonly categoryService = inject(CategoryService);
  private readonly fb = inject(FormBuilder);

  readonly categories = signal<Category[]>([]);

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    type: ['DESPESA' as 'DESPESA' | 'RECEITA', Validators.required],
    colorHex: ['#1e6f5c'],
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
  }

  submit(): void {
    if (this.form.invalid) return;
    this.categoryService.create(this.form.getRawValue()).subscribe(() => {
      this.form.reset({ name: '', type: 'DESPESA', colorHex: '#1e6f5c' });
      this.reload();
    });
  }

  remove(category: Category): void {
    this.categoryService.deactivate(category.id).subscribe(() => this.reload());
  }
}
