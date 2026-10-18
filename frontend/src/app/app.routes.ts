import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { accueilDe, connecteGarde, membreEtudeGarde, roleGarde } from './core/gardes';
import { SessionService } from './core/session.service';

export const routes: Routes = [
  {
    path: 'connexion',
    title: 'Connexion',
    loadComponent: () => import('./pages/connexion.component').then((m) => m.ConnexionComponent),
  },
  {
    path: '',
    canActivate: [connecteGarde],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => accueilDe(inject(SessionService).utilisateur()?.role) },
      { path: 'dashboard', redirectTo: 'tableau-de-bord' },
      {
        path: 'administration/identite',
        title: "Identité de l'étude",
        canActivate: [roleGarde('SUPER_ADMIN')],
        loadComponent: () => import('./pages/identite.component').then((m) => m.IdentiteComponent),
      },
      {
        path: 'equipe',
        title: 'Équipe',
        loadComponent: () => import('./pages/equipe.component').then((m) => m.EquipeComponent),
      },
      {
        path: '',
        canActivateChild: [membreEtudeGarde],
        children: [
          {
            path: 'tableau-de-bord',
            title: 'Tableau de bord',
            loadComponent: () => import('./pages/dashboard.component').then((m) => m.DashboardComponent),
          },
          {
            path: 'dossiers',
            title: 'Dossiers',
            loadComponent: () => import('./pages/dossiers.component').then((m) => m.DossiersComponent),
          },
          {
            path: 'dossiers/:id',
            title: 'Dossier',
            loadComponent: () => import('./pages/dossier-detail.component').then((m) => m.DossierDetailComponent),
          },
          {
            path: 'clients',
            title: 'Clients',
            loadComponent: () => import('./pages/clients.component').then((m) => m.ClientsComponent),
          },
          {
            path: 'clients/:id',
            title: 'Fiche client',
            loadComponent: () => import('./pages/client-fiche.component').then((m) => m.ClientFicheComponent),
          },
          {
            path: 'agenda',
            title: 'Agenda',
            loadComponent: () => import('./pages/agenda.component').then((m) => m.AgendaComponent),
          },
          {
            path: 'repertoire',
            title: 'Répertoire des actes',
            loadComponent: () => import('./pages/repertoire.component').then((m) => m.RepertoireComponent),
          },
          {
            path: 'minutier',
            title: 'Minutier et archives',
            loadComponent: () => import('./pages/minutier.component').then((m) => m.MinutierComponent),
          },
          {
            path: 'comptabilite',
            title: 'Fonds clients',
            loadComponent: () => import('./pages/comptabilite.component').then((m) => m.ComptabiliteComponent),
          },
          {
            path: 'simulateur',
            title: 'Calculateur de frais',
            loadComponent: () => import('./pages/simulateur.component').then((m) => m.SimulateurComponent),
          },
          {
            path: 'catalogue',
            title: 'Catalogue des actes',
            loadComponent: () => import('./pages/catalogue.component').then((m) => m.CatalogueComponent),
          },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
