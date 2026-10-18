import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ApiService } from '../core/api.service';
import { IdentiteService } from '../core/identite.service';
import { NotificationRetard, Role } from '../core/models';
import { SessionService } from '../core/session.service';
import { VERSION } from '../core/version';
import { BadgeNiveauComponent, libelleStatut } from '../shared/badges';
import { IconeComponent, NomIcone } from '../shared/icone.component';

interface Lien {
  lien: string;
  libelle: string;
  icone: NomIcone;
  roles?: Role[];
  exact?: boolean;
}

interface ActionPrincipale {
  libelle: string;
  icone: NomIcone;
  lien: string;
  params?: Record<string, string>;
}

const NOTAIRES: Role[] = ['NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE', 'NOTAIRE_SALARIE'];
const MEMBRES: Role[] = [...NOTAIRES, 'PRINCIPAL_CLERC', 'CLERC', 'COMPTABLE_TAXATEUR', 'FORMALISTE', 'ARCHIVISTE', 'ASSISTANT'];

/** Menu de l'espace de travail : chaque profil ne voit que ce qui le concerne. */
const SECTIONS: { titre: string; liens: Lien[] }[] = [
  {
    titre: 'Travail',
    liens: [
      { lien: '/tableau-de-bord', libelle: 'Tableau de bord', icone: 'accueil', roles: MEMBRES },
      { lien: '/dossiers', libelle: 'Dossiers', icone: 'dossiers', roles: MEMBRES },
      { lien: '/clients', libelle: 'Clients', icone: 'clients', roles: MEMBRES },
      { lien: '/agenda', libelle: 'Agenda', icone: 'agenda', roles: MEMBRES },
    ],
  },
  {
    titre: 'Étude',
    liens: [
      { lien: '/repertoire', libelle: 'Répertoire des actes', icone: 'repertoire',
        roles: [...NOTAIRES, 'PRINCIPAL_CLERC', 'COMPTABLE_TAXATEUR', 'FORMALISTE', 'ARCHIVISTE'] },
      { lien: '/minutier', libelle: 'Minutier et archives', icone: 'minutier',
        roles: [...NOTAIRES, 'PRINCIPAL_CLERC', 'FORMALISTE', 'ARCHIVISTE'] },
      { lien: '/comptabilite', libelle: 'Fonds clients', icone: 'fonds', roles: [...NOTAIRES, 'COMPTABLE_TAXATEUR'] },
      { lien: '/equipe', libelle: 'Équipe', icone: 'equipe', roles: MEMBRES },
    ],
  },
  {
    titre: 'Référentiels',
    liens: [
      { lien: '/catalogue', libelle: 'Catalogue des actes', icone: 'catalogue', roles: MEMBRES },
      { lien: '/simulateur', libelle: 'Calculateur de frais', icone: 'calcul',
        roles: [...NOTAIRES, 'PRINCIPAL_CLERC', 'CLERC', 'COMPTABLE_TAXATEUR', 'ASSISTANT'] },
    ],
  },
  {
    titre: 'Administration',
    liens: [
      { lien: '/administration', libelle: 'Vue d’ensemble', icone: 'accueil', roles: ['SUPER_ADMIN'], exact: true },
      { lien: '/administration/comptes', libelle: 'Comptes utilisateurs', icone: 'equipe', roles: ['SUPER_ADMIN'] },
      { lien: '/administration/securite', libelle: 'Politique de sécurité', icone: 'cadenas', roles: ['SUPER_ADMIN'] },
      { lien: '/administration/journal', libelle: "Journal d'administration", icone: 'journal', roles: ['SUPER_ADMIN'] },
      { lien: '/administration/identite', libelle: "Identité de l'étude", icone: 'identite', roles: ['SUPER_ADMIN'] },
    ],
  },
];

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BadgeNiveauComponent, IconeComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  protected readonly session = inject(SessionService);
  protected readonly identite = inject(IdentiteService).identite;

  protected readonly notifications = signal<NotificationRetard[]>([]);
  protected readonly panneauOuvert = signal(false);
  protected readonly profilOuvert = signal(false);
  protected readonly menuMobile = signal(false);
  protected readonly libelleStatut = libelleStatut;
  protected readonly version = VERSION;

  protected readonly sections = computed(() => {
    const role = this.session.utilisateur()?.role;
    return SECTIONS.map((s) => ({ ...s, liens: s.liens.filter((l) => !l.roles || (role && l.roles.includes(role))) }))
      .filter((s) => s.liens.length);
  });

  /** Bouton principal du menu, propre au métier de chacun. */
  protected readonly actionPrincipale = computed((): ActionPrincipale | null => {
    switch (this.session.utilisateur()?.role) {
      case 'ARCHIVISTE':
        return { libelle: 'Archiver des minutes', icone: 'archiver', lien: '/minutier', params: { etat: 'A_ARCHIVER' } };
      case 'COMPTABLE_TAXATEUR':
        return { libelle: 'Fonds à certifier', icone: 'fonds2', lien: '/dossiers', params: { statut: 'FUNDING' } };
      case 'FORMALISTE':
        return { libelle: 'Formalités à suivre', icone: 'repertoire', lien: '/dossiers', params: { statut: 'POST_ACT' } };
      case 'SUPER_ADMIN':
        return { libelle: 'Nouveau compte', icone: 'plus', lien: '/administration/comptes', params: { nouveau: '1' } };
      default:
        return { libelle: 'Nouveau dossier', icone: 'plus', lien: '/dossiers', params: { nouveau: '1' } };
    }
  });

  protected readonly estMembre = computed(() => !this.session.a('SUPER_ADMIN'));

  @HostListener('document:click', ['$event'])
  protected surClic(e: MouseEvent): void {
    const cible = e.target as HTMLElement;
    if (this.panneauOuvert() && !cible.closest('.cloche')) this.panneauOuvert.set(false);
    if (this.profilOuvert() && !cible.closest('.profil')) this.profilOuvert.set(false);
  }

  @HostListener('document:keydown.escape')
  protected surEchap(): void {
    this.panneauOuvert.set(false);
    this.profilOuvert.set(false);
    this.menuMobile.set(false);
  }

  ngOnInit(): void {
    if (this.estMembre()) {
      this.api.utilisateurs().subscribe((u) => this.session.utilisateurs.set(u));
      this.chargerNotifications();
    }
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.panneauOuvert.set(false);
      this.profilOuvert.set(false);
      this.menuMobile.set(false);
      if (this.estMembre()) this.chargerNotifications();
    });
  }

  protected rechercher(terme: string): void {
    const q = terme.trim();
    if (q) this.router.navigate(['/dossiers'], { queryParams: { q } });
  }

  protected deconnexion(): void {
    this.session.fermer();
    this.router.navigate(['/connexion']);
  }

  private chargerNotifications(): void {
    this.api.notifications().subscribe({ next: (n) => this.notifications.set(n), error: () => this.notifications.set([]) });
  }
}
