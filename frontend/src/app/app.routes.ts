import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Tableau de bord — Notarius',
    loadComponent: () => import('./pages/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'dossiers',
    title: 'Dossiers — Notarius',
    loadComponent: () => import('./pages/dossiers.component').then((m) => m.DossiersComponent),
  },
  {
    path: 'dossiers/:id',
    title: 'Dossier — Notarius',
    loadComponent: () => import('./pages/dossier-detail.component').then((m) => m.DossierDetailComponent),
  },
  {
    path: 'comptabilite',
    title: 'Fonds clients & CDC — Notarius',
    loadComponent: () => import('./pages/comptabilite.component').then((m) => m.ComptabiliteComponent),
  },
  {
    path: 'simulateur',
    title: 'Calculateur de frais — Notarius',
    loadComponent: () => import('./pages/simulateur.component').then((m) => m.SimulateurComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];
