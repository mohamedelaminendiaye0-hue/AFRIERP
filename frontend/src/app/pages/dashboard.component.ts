import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { DossierResume, LigneMinutier, Role, StatutDossier, TableauDeBord } from '../core/models';
import { SessionService } from '../core/session.service';
import { BadgeNiveauComponent, BadgeStatutComponent, libelleStatut } from '../shared/badges';
import { PourcentagePipe, XofPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';

/** Carte-raccourci : un chiffre et le lien vers la liste qu'il résume. */
interface Raccourci {
  libelle: string;
  valeur: string;
  contexte: string;
  aller: string;
  lien: string;
  params?: Record<string, string>;
  ton?: 'alerte' | 'accent';
}

const PREFIXE_SIGNATURE = 'SIGNATURE';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, BadgeStatutComponent, BadgeNiveauComponent, XofPipe, PourcentagePipe, IconeComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);
  protected readonly session = inject(SessionService);

  protected readonly tableau = signal<TableauDeBord | null>(null);
  protected readonly dossiers = signal<DossierResume[]>([]);
  protected readonly minutier = signal<LigneMinutier[]>([]);
  protected readonly erreur = signal(false);
  protected readonly libelleStatut = libelleStatut;

  protected readonly role = computed(() => this.session.utilisateur()?.role);
  protected readonly pilotage = computed(() => this.est('NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE', 'NOTAIRE_SALARIE', 'PRINCIPAL_CLERC'));
  protected readonly finances = computed(() => this.est('NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE', 'COMPTABLE_TAXATEUR'));

  protected readonly salutation = computed(() => {
    const u = this.session.utilisateur();
    if (!u) return 'Bonjour';
    return u.notaire ? `Bonjour, Maître ${u.nom}` : `Bonjour, ${u.prenom}`;
  });

  protected readonly aujourdhui = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
    .format(new Date());

  protected readonly maxPipeline = computed(() => Math.max(1, ...(this.tableau()?.pipeline.map((p) => p.dossiers) ?? [1])));
  protected readonly maxCharge = computed(() => Math.max(1, ...(this.tableau()?.charges.map((c) => c.dossiersActifs) ?? [1])));

  protected readonly signaturesDuJour = computed(() =>
    (this.tableau()?.rendezVousDuJour ?? []).filter((r) => r.type.startsWith(PREFIXE_SIGNATURE)),
  );

  private readonly miens = computed(() => {
    const id = this.session.id();
    return this.dossiers().filter((d) => d.clercId === id || d.notaireId === id);
  });

  protected readonly resume = computed(() => {
    const t = this.tableau();
    const u = this.session.utilisateur();
    if (!t || !u) return '';
    const retards = t.notifications.length;
    switch (u.role) {
      case 'CLERC':
        return `${this.miens().filter((d) => d.statut !== 'CLOSED').length} dossiers vous sont confiés · ${this.miens().filter((d) => d.retardStatutJours > 0).length} en retard sur le délai cible.`;
      case 'ARCHIVISTE':
        return `${this.minutier().filter((m) => m.etat === 'A_ARCHIVER').length} acte(s) prêt(s) à être archivé(s) au répertoire.`;
      default:
        return `${this.signaturesDuJour().length} signature(s) prévue(s) aujourd'hui · ${retards} dossier(s) au-delà du délai cible.`;
    }
  });

  protected readonly raccourcis = computed<Raccourci[]>(() => {
    const t = this.tableau();
    const role = this.role();
    if (!t || !role) return [];
    const parStatut = (s: StatutDossier) => this.dossiers().filter((d) => d.statut === s).length;
    const n = (v: number) => v.toLocaleString('fr-FR');
    const signatures: Raccourci = {
      libelle: "À signer aujourd'hui", valeur: n(this.signaturesDuJour().length), ton: 'accent',
      contexte: this.signaturesDuJour()[0] ? `Prochaine : ${this.signaturesDuJour()[0].debut.slice(11, 16).replace(':', ' h ')} · ${this.signaturesDuJour()[0].clientNom ?? ''}` : 'Aucune signature planifiée',
      aller: 'Agenda des signatures', lien: '/agenda',
    };
    const retards: Raccourci = {
      libelle: 'Dossiers en retard', valeur: n(t.notifications.length), ton: t.notifications.length ? 'alerte' : undefined,
      contexte: `dont ${t.notifications.filter((x) => x.niveau === 'CRITIQUE').length} au niveau critique`,
      aller: `Voir les ${t.notifications.length} dossiers`, lien: '/dossiers', params: { vue: 'retard' },
    };
    const aAssigner: Raccourci = {
      libelle: 'À assigner', valeur: n(t.dossiersAAssigner), contexte: 'Dossiers ouverts sans clerc rédacteur',
      aller: 'Répartir les dossiers', lien: '/dossiers', params: { vue: 'a-assigner' },
    };
    const fonds: Raccourci = {
      libelle: 'Fonds en séquestre', valeur: this.millions(t.kpi.encoursFondsClients),
      contexte: t.controle.conforme ? 'FCFA · comptes clients équilibrés' : 'FCFA · écart de rapprochement à traiter',
      ton: t.controle.conforme ? undefined : 'alerte', aller: 'Fonds clients', lien: '/comptabilite',
    };
    const rdv: Raccourci = {
      libelle: "Rendez-vous aujourd'hui", valeur: n(t.rendezVousDuJour.length),
      contexte: t.rendezVousDuJour[0] ? `Premier : ${t.rendezVousDuJour[0].debut.slice(11, 16).replace(':', ' h ')}` : 'Agenda libre',
      aller: "Ouvrir l'agenda", lien: '/agenda',
    };
    const statut = (s: StatutDossier, libelle: string, contexte: string, aller: string, ton?: 'alerte' | 'accent'): Raccourci => ({
      libelle, valeur: n(parStatut(s)), contexte, aller, ton, lien: '/dossiers', params: { statut: s },
    });
    const aArchiver = this.minutier().filter((m) => m.etat === 'A_ARCHIVER').length;
    switch (role) {
      case 'NOTAIRE_TITULAIRE':
      case 'NOTAIRE_ASSOCIE':
        return [signatures, retards, aAssigner, fonds];
      case 'NOTAIRE_SALARIE':
        return [signatures, retards, statut('REVIEW', 'Projets à valider', 'Projets d\'actes en relecture', 'Ouvrir les projets'), rdv];
      case 'PRINCIPAL_CLERC':
        return [aAssigner, retards, statut('REVIEW', 'En relecture', 'Projets soumis aux notaires', 'Voir les projets'), rdv];
      case 'CLERC': {
        const miens = this.miens().filter((d) => d.statut !== 'CLOSED');
        return [
          { libelle: 'Mes dossiers', valeur: n(miens.length), contexte: 'Dossiers qui vous sont confiés', aller: 'Ouvrir mes dossiers', lien: '/dossiers', params: { vue: 'miens' } },
          { libelle: 'Mes retards', valeur: n(miens.filter((d) => d.retardStatutJours > 0).length), ton: miens.some((d) => d.retardStatutJours > 0) ? 'alerte' : undefined,
            contexte: 'Au-delà du délai cible du statut', aller: 'Voir mes retards', lien: '/dossiers', params: { vue: 'retard' } },
          { libelle: 'Pièces à obtenir', valeur: n(miens.filter((d) => d.statut === 'PENDING_DOCS').length), contexte: 'Dossiers en instruction',
            aller: 'Relancer les pièces', lien: '/dossiers', params: { statut: 'PENDING_DOCS' } },
          rdv,
        ];
      }
      case 'COMPTABLE_TAXATEUR':
        return [
          statut('TAXING', 'À taxer', 'Décomptes prévisionnels à établir', 'Ouvrir les dossiers'),
          statut('FUNDING', 'Provisions attendues', 'Fonds à encaisser puis certifier', 'Suivre les provisions', 'accent'),
          { libelle: 'Alertes de conformité', valeur: n(t.alertes.length), ton: t.alertes.length ? 'alerte' : undefined,
            contexte: 'Sous-comptes, CDC, rapprochement', aller: 'Voir les alertes', lien: '/comptabilite' },
          fonds,
        ];
      case 'FORMALISTE':
        return [
          statut('SIGNED', 'Actes signés', 'Formalités à engager', 'Engager les formalités', 'accent'),
          statut('POST_ACT', 'Formalités en cours', 'Dépôts et suivis administratifs', 'Suivre les formalités'),
          retards,
          { libelle: 'Répertoire', valeur: n(this.minutier().length), contexte: 'Actes inscrits cette année', aller: 'Ouvrir le répertoire', lien: '/repertoire' },
        ];
      case 'ARCHIVISTE':
        return [
          { libelle: 'Actes à archiver', valeur: n(aArchiver), ton: aArchiver ? 'accent' : undefined, contexte: 'Dossiers prêts à être clôturés',
            aller: 'Archiver les actes', lien: '/repertoire', params: { etat: 'A_ARCHIVER' } },
          { libelle: 'Actes archivés', valeur: n(this.minutier().filter((m) => m.etat === 'ARCHIVEE').length), contexte: 'Conservation 75 ans',
            aller: 'Consulter les archives', lien: '/repertoire', params: { etat: 'ARCHIVEE' } },
          { libelle: 'Formalités en cours', valeur: n(this.minutier().filter((m) => m.etat === 'FORMALITES').length), contexte: 'Minutes signées, pas encore archivables',
            aller: 'Voir les minutes', lien: '/repertoire', params: { etat: 'FORMALITES' } },
          { libelle: 'Répertoire des actes', valeur: n(this.minutier().length), contexte: 'Actes inscrits', aller: 'Ouvrir le répertoire', lien: '/repertoire' },
        ];
      default:
        return [
          rdv,
          { libelle: 'Nouveaux dossiers', valeur: n(parStatut('DRAFT')), contexte: 'Ouverts, en création', aller: 'Voir les dossiers', lien: '/dossiers', params: { statut: 'DRAFT' } },
          statut('READY_TO_SIGN', 'Prêts à signer', 'Rendez-vous de signature à fixer', 'Planifier les signatures', 'accent'),
          aAssigner,
        ];
    }
  });

  ngOnInit(): void {
    const archives = this.est('ARCHIVISTE', 'FORMALISTE');
    forkJoin({ t: this.api.tableauDeBord(), d: this.api.dossiers() }).subscribe({
      next: ({ t, d }) => {
        this.tableau.set(t);
        this.dossiers.set(d);
      },
      error: () => this.erreur.set(true),
    });
    if (archives) this.api.minutier().subscribe((m) => this.minutier.set(m));
  }

  private est(...roles: Role[]): boolean {
    return this.session.a(...roles);
  }

  private millions(v: number): string {
    return v >= 1_000_000 ? `${(v / 1_000_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} M` : v.toLocaleString('fr-FR');
  }
}
