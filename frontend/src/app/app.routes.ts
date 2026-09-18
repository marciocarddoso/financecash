import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'lancamentos',
    canActivate: [authGuard],
    loadComponent: () => import('./features/entries/entries-list.component').then((m) => m.EntriesListComponent),
  },
  {
    path: 'recorrencias',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/recurring-rules/recurring-rules.component').then((m) => m.RecurringRulesComponent),
  },
  {
    path: 'parcelamentos',
    canActivate: [authGuard],
    loadComponent: () => import('./features/installments/installments.component').then((m) => m.InstallmentsComponent),
  },
  {
    path: 'importar',
    canActivate: [authGuard],
    loadComponent: () => import('./features/importar/importar.component').then((m) => m.ImportarComponent),
  },
  {
    path: 'fechamento',
    canActivate: [authGuard],
    loadComponent: () => import('./features/fechamento/fechamento.component').then((m) => m.FechamentoComponent),
  },
  {
    path: 'categorias',
    canActivate: [authGuard],
    loadComponent: () => import('./features/categories/categories.component').then((m) => m.CategoriesComponent),
  },
  {
    path: 'contas',
    canActivate: [authGuard],
    loadComponent: () => import('./features/accounts/accounts.component').then((m) => m.AccountsComponent),
  },
  {
    path: 'cartoes',
    canActivate: [authGuard],
    loadComponent: () => import('./features/credit-cards/credit-cards.component').then((m) => m.CreditCardsComponent),
  },
  {
    path: 'relatorios',
    canActivate: [authGuard],
    loadComponent: () => import('./features/reports/reports.component').then((m) => m.ReportsComponent),
  },
  {
    path: 'preferencias',
    canActivate: [authGuard],
    loadComponent: () => import('./features/preferencias/preferencias.component').then((m) => m.PreferenciasComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];
