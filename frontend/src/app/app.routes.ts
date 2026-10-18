import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { accueilDe, connecteGarde, membreEtudeGarde, roleGarde, sessionGarde } from './core/gardes';
import { SessionService } from './core/session.service';

export const routes: Routes = [
  {
    path: 'connexion',
    title: 'Connexion',
    loadComponent: () => import('./pages/connexion.component').then((m) => m.ConnexionComponent),
  },
  {
    path: 'mot-de-passe',
    title: 'Mot de passe',
    canActivate: [sessionGarde],
    loadComponent: () => import('./pages/mot-de-passe.component').then((m) => m.MotDePasseComponent),
  },
  {
    path: '',
    canActivate: [connecteGarde],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => accueilDe(inject(SessionService).utilisateur()?.role) },
      { path: 'dashboard', redirectTo: 'tableau-de-bord' },
      {
        path: 'administration',
        canActivateChild: [roleGarde('SUPER_ADMIN')],
        children: [
          {
            path: '',
            pathMatch: 'full',
            title: 'Administration',
            loadComponent: () => import('./pages/admin/admin-tableau.component').then((m) => m.AdminTableauComponent),
          },
          {
            path: 'comptes',
            title: 'Comptes utilisateurs',
            loadComponent: () => import('./pages/admin/admin-comptes.component').then((m) => m.AdminComptesComponent),
          },
          {
            path: 'securite',
            title: 'Politique de sécurité',
            loadComponent: () => import('./pages/admin/admin-securite.component').then((m) => m.AdminSecuriteComponent),
          },
          {
            path: 'journal',
            title: "Journal d'administration",
            loadComponent: () => import('./pages/admin/admin-journal.component').then((m) => m.AdminJournalComponent),
          },
          {
            path: 'identite',
            title: "Identité de l'étude",
            loadComponent: () => import('./pages/identite.component').then((m) => m.IdentiteComponent),
          },
        ],
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
          // Les archives des minutes sont tenues dans le répertoire des actes.
          { path: 'minutier', redirectTo: 'repertoire' },
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
