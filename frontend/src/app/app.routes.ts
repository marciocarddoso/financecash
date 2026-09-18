import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'lancamentos',
    loadComponent: () => import('./features/entries/entries-list.component').then((m) => m.EntriesListComponent),
  },
  {
    path: 'categorias',
    loadComponent: () => import('./features/categories/categories.component').then((m) => m.CategoriesComponent),
  },
  {
    path: 'contas',
    loadComponent: () => import('./features/accounts/accounts.component').then((m) => m.AccountsComponent),
  },
  {
    path: 'relatorios',
    loadComponent: () => import('./features/reports/reports.component').then((m) => m.ReportsComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];
