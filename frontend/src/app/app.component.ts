import { Component, OnInit, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ApiService } from './core/api.service';
import { NotificationRetard } from './core/models';
import { SessionService } from './core/session.service';
import { BadgeNiveauComponent, libelleStatut } from './shared/badges';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BadgeNiveauComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  protected readonly session = inject(SessionService);

  protected readonly notifications = signal<NotificationRetard[]>([]);
  protected readonly panneauOuvert = signal(false);
  protected readonly libelleStatut = libelleStatut;

  protected readonly navigation = [
    { lien: '/dashboard', libelle: 'Tableau de bord', sousTitre: 'Pilotage de l\'etude' },
    { lien: '/dossiers', libelle: 'Dossiers', sousTitre: 'Cycle de vie des actes' },
    { lien: '/agenda', libelle: 'Agenda', sousTitre: 'Prise de rendez-vous' },
    { lien: '/comptabilite', libelle: 'Fonds clients & CDC', sousTitre: 'Maniement de fonds' },
    { lien: '/simulateur', libelle: 'Calculateur de frais', sousTitre: 'Emoluments & taxes' },
    { lien: '/minutier', libelle: 'Minutier', sousTitre: 'Minutes archivees' },
    { lien: '/equipe', libelle: 'Equipe', sousTitre: "Organisation de l'etude" },
  ];

  constructor() {
    // Les notifications dependent de l'utilisateur qui agit (clerc / notaire destinataires).
    effect(() => {
      this.session.id();
      this.chargerNotifications();
    });
  }

  ngOnInit(): void {
    this.api.utilisateurs().subscribe((u) => this.session.utilisateurs.set(u));
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.panneauOuvert.set(false);
      this.chargerNotifications();
    });
  }

  protected changerUtilisateur(id: string): void {
    this.session.changer(id);
    // Recharge la page courante : verdicts et actions dependent du profil.
    const url = this.router.url;
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => this.router.navigateByUrl(url));
  }

  private chargerNotifications(): void {
    this.api.notifications().subscribe({ next: (n) => this.notifications.set(n), error: () => this.notifications.set([]) });
  }
}
