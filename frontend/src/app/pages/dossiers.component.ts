import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, NouveauDossier, lireErreur } from '../core/api.service';
import { CatalogueService } from '../core/catalogue.service';
import { ClientResume, DomaineActe, DossierResume, ErreurApi, StatutDossier, TypeActe } from '../core/models';
import { SessionService } from '../core/session.service';
import { BadgeStatutComponent, libelleStatut } from '../shared/badges';
import { ConfirmationComponent } from '../shared/confirmation.component';
import { DateFrPipe, XofPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';

type Vue = 'en-cours' | 'miens' | 'retard' | 'a-assigner' | 'archives' | 'tous';
type Tri = 'echeance' | 'ouverture' | 'reference' | 'retard';

const ORDRE: StatutDossier[] = ['DRAFT', 'PENDING_DOCS', 'DRAFTING', 'REVIEW', 'TAXING', 'FUNDING', 'READY_TO_SIGN', 'SIGNED', 'POST_ACT', 'CLOSED'];
const QUALITES: [string, string][] = [
  ['VENDEUR', 'Vendeur'], ['ACQUEREUR', 'Acquéreur'], ['DONATEUR', 'Donateur'], ['DONATAIRE', 'Donataire'],
  ['HERITIER', 'Héritier'], ['EMPRUNTEUR', 'Emprunteur'], ['BANQUE', 'Banque'], ['ASSOCIE', 'Associé'],
  ['CEDANT', 'Cédant'], ['CESSIONNAIRE', 'Cessionnaire'], ['EPOUX', 'Époux'], ['MANDANT', 'Mandant'],
  ['MANDATAIRE', 'Mandataire'], ['SOCIETE', 'Société'],
];

@Component({
  selector: 'app-dossiers',
  standalone: true,
  imports: [FormsModule, RouterLink, BadgeStatutComponent, XofPipe, DateFrPipe, IconeComponent, ConfirmationComponent],
  templateUrl: './dossiers.component.html',
  styleUrl: './dossiers.component.scss',
})
export class DossiersComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);
  protected readonly session = inject(SessionService);
  protected readonly catalogue = inject(CatalogueService);

  /* Paramètres d'adresse : les raccourcis du tableau de bord ouvrent une liste déjà filtrée. */
  readonly vue = input<string>();
  readonly statut = input<string>();
  readonly q = input<string>();
  readonly nouveau = input<string>();
  readonly client = input<string>();

  protected readonly ordre = ORDRE;
  protected readonly qualites = QUALITES;
  protected readonly libelleStatut = libelleStatut;

  protected readonly dossiers = signal<DossierResume[]>([]);
  protected readonly clients = signal<ClientResume[]>([]);
  protected readonly vueActive = signal<Vue>('en-cours');
  protected readonly etape = signal<StatutDossier | ''>('');
  protected readonly domaine = signal<DomaineActe | ''>('');
  protected readonly recherche = signal('');
  protected readonly tri = signal<Tri>('echeance');
  protected readonly selection = signal<Set<string>>(new Set());
  protected readonly formulaireOuvert = signal(false);
  protected readonly erreur = signal<ErreurApi | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly archivage = signal<string[] | null>(null);
  protected readonly occupe = signal(false);
  protected clercReassignation = '';
  protected form = this.formulaireVide();

  protected readonly encadrement = computed(() => !!this.session.utilisateur()?.encadrement);
  protected readonly peutCreer = computed(() =>
    this.session.a('ASSISTANT', 'CLERC', 'PRINCIPAL_CLERC', 'NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE', 'NOTAIRE_SALARIE'),
  );
  protected readonly peutArchiver = computed(() => this.session.a('ARCHIVISTE', 'NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE'));

  protected readonly vues: { cle: Vue; libelle: string; predicat: (d: DossierResume) => boolean }[] = [
    { cle: 'en-cours', libelle: 'En cours', predicat: (d) => d.statut !== 'CLOSED' },
    { cle: 'miens', libelle: 'Mes dossiers', predicat: (d) => d.statut !== 'CLOSED' && (d.clercId === this.session.id() || d.notaireId === this.session.id()) },
    { cle: 'retard', libelle: 'En retard', predicat: (d) => d.retardStatutJours > 0 || d.retardSignatureJours > 0 },
    { cle: 'a-assigner', libelle: 'À assigner', predicat: (d) => d.clercId === null && d.statut !== 'CLOSED' },
    { cle: 'archives', libelle: 'Archivés', predicat: (d) => d.statut === 'CLOSED' },
    { cle: 'tous', libelle: 'Tous', predicat: () => true },
  ];

  protected readonly visibles = computed(() => {
    const vue = this.vues.find((v) => v.cle === this.vueActive())!;
    const q = normaliser(this.recherche());
    const liste = this.dossiers()
      .filter(vue.predicat)
      .filter((d) => !this.etape() || d.statut === this.etape())
      .filter((d) => !this.domaine() || d.domaine === this.domaine())
      .filter((d) => !q || normaliser(`${d.id} ${d.reference} ${d.intitule} ${d.clercNom ?? ''} ${d.notaireNom} ${d.typeActeLibelle}`).includes(q));
    const echeance = (d: DossierResume) => d.dateSignaturePrevue ?? '9999';
    const comparateurs: Record<Tri, (a: DossierResume, b: DossierResume) => number> = {
      echeance: (a, b) => echeance(a).localeCompare(echeance(b)),
      ouverture: (a, b) => b.dateOuverture.localeCompare(a.dateOuverture),
      reference: (a, b) => a.reference.localeCompare(b.reference),
      retard: (a, b) => b.retardStatutJours - a.retardStatutJours,
    };
    return [...liste].sort(comparateurs[this.tri()]);
  });

  protected readonly selectionnes = computed(() => this.dossiers().filter((d) => this.selection().has(d.id)));
  protected readonly archivablesSelection = computed(() => this.selectionnes().filter((d) => d.statut === 'POST_ACT').length);
  protected readonly toutSelectionne = computed(() => this.visibles().length > 0 && this.visibles().every((d) => this.selection().has(d.id)));
  protected readonly clercs = computed(() =>
    this.session.utilisateurs().filter((u) => (u.role === 'CLERC' || u.role === 'PRINCIPAL_CLERC') && u.actif),
  );
  protected readonly notaires = computed(() => this.session.utilisateurs().filter((u) => u.notaire && u.actif));
  protected readonly hypotheques = computed(() => this.dossiers().filter((d) => d.typeActe === 'AFFECTATION_HYPOTHECAIRE'));
  protected readonly charge = computed(() => {
    const c: Record<string, number | undefined> = {};
    this.dossiers().filter((d) => d.statut !== 'CLOSED' && d.clercId).forEach((d) => (c[d.clercId!] = (c[d.clercId!] ?? 0) + 1));
    return c;
  });

  ngOnInit(): void {
    const vue = this.vue();
    if (vue && this.vues.some((v) => v.cle === vue)) this.vueActive.set(vue as Vue);
    else if (this.session.a('CLERC')) this.vueActive.set('miens');
    const statut = this.statut();
    if (statut && (ORDRE as string[]).includes(statut)) {
      this.etape.set(statut as StatutDossier);
      if (statut === 'CLOSED') this.vueActive.set('archives');
    }
    if (this.q()) this.recherche.set(this.q()!);
    if (this.nouveau() && this.peutCreer()) this.ouvrirFormulaire();
    this.charger();
  }

  protected compte(cle: Vue): number {
    const v = this.vues.find((x) => x.cle === cle)!;
    return this.dossiers().filter(v.predicat).length;
  }

  protected rang(s: StatutDossier): number {
    return ORDRE.indexOf(s) + 1;
  }

  protected ouvrir(d: DossierResume): void {
    this.router.navigate(['/dossiers', d.id]);
  }

  protected basculer(id: string): void {
    const s = new Set(this.selection());
    if (s.has(id)) s.delete(id);
    else s.add(id);
    this.selection.set(s);
  }

  protected viderSelection(): void {
    this.selection.set(new Set());
  }

  protected basculerTout(): void {
    this.selection.set(this.toutSelectionne() ? new Set() : new Set(this.visibles().map((d) => d.id)));
  }

  /* --- Actions groupées --- */

  protected reassigner(): void {
    const ids = [...this.selection()].filter((id) => this.dossiers().find((d) => d.id === id)?.statut !== 'CLOSED');
    if (!this.clercReassignation || !ids.length) return;
    const nom = this.clercs().find((u) => u.id === this.clercReassignation)?.nomComplet;
    forkJoin(ids.map((id) => this.api.assigner(id, { clercId: this.clercReassignation }))).subscribe({
      next: () => this.terminer(`${ids.length} dossier(s) confié(s) à ${nom}.`),
      error: (e) => this.erreur.set(lireErreur(e)),
    });
  }

  protected demanderArchivage(): void {
    this.archivage.set(this.selectionnes().filter((d) => d.statut === 'POST_ACT').map((d) => d.id));
  }

  protected archiver(): void {
    const ids = this.archivage();
    if (!ids?.length) return;
    this.occupe.set(true);
    this.api.archiver(ids, null).subscribe({
      next: (r) => {
        this.occupe.set(false);
        this.archivage.set(null);
        const refus = r.filter((x) => !x.archive);
        this.terminer(`${r.length - refus.length} dossier(s) archivé(s).` +
          (refus.length ? ` Non archivés : ${refus.map((x) => `${x.dossierId} (${x.blocages[0]?.message})`).join(' ; ')}` : ''));
      },
      error: (e) => {
        this.occupe.set(false);
        this.archivage.set(null);
        this.erreur.set(lireErreur(e));
      },
    });
  }

  protected exporter(): void {
    const lignes = this.selectionnes().length ? this.selectionnes() : this.visibles();
    const entetes = ['Dossier', 'Référence', 'Intitulé', "Type d'acte", 'Étape', 'Clerc', 'Notaire', 'Ouverture', 'Signature prévue', 'Montant (FCFA)'];
    const csv = [entetes, ...lignes.map((d) => [d.id, d.reference, d.intitule, d.typeActeLibelle, libelleStatut(d.statut), d.clercNom ?? '',
      d.notaireNom, d.dateOuverture, d.dateSignaturePrevue ?? '', String(d.montantTransaction)])]
      .map((l) => l.map((c) => `"${c.replace(/"/g, '""')}"`).join(';')).join('\r\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    a.download = 'dossiers.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }

  private terminer(message: string): void {
    this.message.set(message);
    this.erreur.set(null);
    this.selection.set(new Set());
    this.clercReassignation = '';
    this.charger();
  }

  /* --- Ouverture d'un dossier --- */

  protected ouvrirFormulaire(): void {
    this.form = this.formulaireVide();
    const clientId = this.client();
    if (clientId) this.form.clientId = clientId;
    if (this.session.utilisateur()?.notaire) this.form.notaireId = this.session.id();
    if (this.session.a('CLERC')) this.form.clercId = this.session.id();
    this.erreur.set(null);
    this.formulaireOuvert.set(true);
    if (!this.clients().length) this.api.clients().subscribe((c) => this.clients.set(c));
  }

  protected typeChoisi() {
    return this.catalogue.type(this.form.typeActe);
  }

  protected creer(): void {
    this.erreur.set(null);
    const f = this.form;
    const partie = f.clientId
      ? [{ clientId: f.clientId, qualite: f.qualite }]
      : f.partie.trim()
        ? [{ type: f.partieType, nom: f.partie.trim(), qualite: f.qualite, identifiant: f.identifiant || undefined }]
        : [];
    const cmd: NouveauDossier = {
      intitule: f.intitule,
      typeActe: f.typeActe,
      montantTransaction: Number(f.montant) || 0,
      prixAcquisition: Number(f.prixAcquisition) || null,
      clercId: f.clercId || null,
      dossierLieId: f.dossierLieId || null,
      notaireId: f.notaireId,
      bien: f.designation ? { designation: f.designation, titreFoncier: f.titreFoncier, adresse: '', commune: f.commune } : null,
      intervenants: partie,
    };
    this.api.creerDossier(cmd).subscribe({
      next: (d) => this.router.navigate(['/dossiers', d.resume.id]),
      error: (e) => this.erreur.set(lireErreur(e)),
    });
  }

  private charger(): void {
    this.api.dossiers().subscribe((d) => this.dossiers.set(d));
  }

  private formulaireVide() {
    return {
      intitule: '',
      typeActe: 'VENTE_IMMOBILIERE' as TypeActe,
      montant: '' as string | number,
      prixAcquisition: '' as string | number,
      clercId: '',
      notaireId: '',
      designation: '',
      titreFoncier: '',
      commune: '',
      clientId: '',
      partie: '',
      identifiant: '',
      partieType: 'PHYSIQUE' as 'PHYSIQUE' | 'MORALE',
      qualite: 'VENDEUR',
      dossierLieId: '',
    };
  }
}

function normaliser(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
