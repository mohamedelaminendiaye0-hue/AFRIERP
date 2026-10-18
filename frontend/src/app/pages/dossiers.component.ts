import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService, NouveauDossier, lireErreur } from '../core/api.service';
import { CatalogueService } from '../core/catalogue.service';
import { DomaineActe, DossierResume, ErreurApi, TypeActe } from '../core/models';
import { SessionService } from '../core/session.service';
import { BadgeStatutComponent } from '../shared/badges';
import { PourcentagePipe, XofPipe } from '../shared/format.pipes';

interface Filtre {
  cle: string;
  libelle: string;
  predicat: (d: DossierResume) => boolean;
}

@Component({
  selector: 'app-dossiers',
  standalone: true,
  imports: [FormsModule, BadgeStatutComponent, XofPipe, PourcentagePipe],
  templateUrl: './dossiers.component.html',
})
export class DossiersComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);
  protected readonly session = inject(SessionService);

  protected readonly dossiers = signal<DossierResume[]>([]);
  protected readonly recherche = signal('');
  protected readonly filtreActif = signal('tous');
  protected readonly formulaireOuvert = signal(false);
  protected readonly erreur = signal<ErreurApi | null>(null);
  protected readonly catalogue = inject(CatalogueService);
  protected readonly domaineFiltre = signal<DomaineActe | ''>('');

  protected readonly filtres: Filtre[] = [
    { cle: 'tous', libelle: 'Tous', predicat: () => true },
    { cle: 'miens', libelle: 'Mes dossiers', predicat: (d) => d.clercId === this.session.id() || d.notaireId === this.session.id() },
    { cle: 'assigner', libelle: 'A assigner', predicat: (d) => d.clercId === null && d.statut !== 'CLOSED' },
    { cle: 'instruction', libelle: 'Instruction & redaction', predicat: (d) => ['DRAFT', 'PENDING_DOCS', 'DRAFTING', 'REVIEW'].includes(d.statut) },
    { cle: 'fonds', libelle: 'Taxage & provision', predicat: (d) => ['TAXING', 'FUNDING'].includes(d.statut) },
    { cle: 'signature', libelle: 'Signature', predicat: (d) => ['READY_TO_SIGN', 'SIGNED'].includes(d.statut) },
    { cle: 'formalites', libelle: 'Formalites', predicat: (d) => d.statut === 'POST_ACT' },
    { cle: 'gainde', libelle: 'GAINDE 2000', predicat: (d) => d.modeSignature === 'ELECTRONIQUE_GAINDE' },
    { cle: 'retard', libelle: 'En retard', predicat: (d) => d.retardStatutJours > 0 || d.retardSignatureJours > 0 },
    { cle: 'cdc', libelle: 'Alerte CDC', predicat: (d) => d.alerteCdc },
    { cle: 'clos', libelle: 'Archives', predicat: (d) => d.statut === 'CLOSED' },
  ];

  protected readonly visibles = computed(() => {
    const filtre = this.filtres.find((f) => f.cle === this.filtreActif())!;
    const q = this.recherche().trim().toLowerCase();
    const domaine = this.domaineFiltre();
    return this.dossiers()
      .filter(filtre.predicat)
      .filter((d) => !domaine || d.domaine === domaine)
      .filter((d) => !q || `${d.reference} ${d.intitule} ${d.clercNom} ${d.typeActeLibelle}`.toLowerCase().includes(q));
  });

  protected readonly clercs = computed(() =>
    this.session.utilisateurs().filter((u) => (u.role === 'CLERC' || u.role === 'PRINCIPAL_CLERC') && u.actif),
  );
  protected readonly notaires = computed(() => this.session.utilisateurs().filter((u) => u.notaire && u.actif));

  protected readonly hypotheques = computed(() => this.dossiers().filter((d) => d.typeActe === 'AFFECTATION_HYPOTHECAIRE'));

  protected typeChoisi() {
    return this.catalogue.type(this.nouveau.typeActe);
  }

  /** Charge actuelle de chaque clerc (planning de production) pour guider l'affectation. */
  protected readonly charge = computed(() => {
    const c: Record<string, number | undefined> = {};
    this.dossiers().filter((d) => d.statut !== 'CLOSED' && d.clercId).forEach((d) => (c[d.clercId!] = (c[d.clercId!] ?? 0) + 1));
    return c;
  });

  protected affectations: Record<string, string> = {};
  protected readonly message = signal<string | null>(null);

  /* --- Formulaire d'ouverture --- */
  protected nouveau = this.formulaireVide();

  ngOnInit(): void {
    this.charger();
  }

  protected compte(f: Filtre): number {
    return this.dossiers().filter(f.predicat).length;
  }

  protected creer(): void {
    this.erreur.set(null);
    const n = this.nouveau;
    const cmd: NouveauDossier = {
      intitule: n.intitule,
      typeActe: n.typeActe,
      montantTransaction: Number(n.montant) || 0,
      prixAcquisition: Number(n.prixAcquisition) || null,
      clercId: n.clercId || null,
      dossierLieId: n.dossierLieId || null,
      notaireId: n.notaireId,
      bien: n.designation ? { designation: n.designation, titreFoncier: n.titreFoncier, adresse: n.adresse, commune: n.commune } : null,
      intervenants: n.partie ? [{ type: n.partieType, nom: n.partie, qualite: n.partieQualite }] : [],
    };
    this.api.creerDossier(cmd).subscribe({
      next: (d) => this.router.navigate(['/dossiers', d.resume.id]),
      error: (e) => this.erreur.set(lireErreur(e)),
    });
  }

  /** Affectation rapide depuis la liste (principal clerc / notaires en titre). */
  protected affecter(d: DossierResume, evenement: Event): void {
    evenement.stopPropagation();
    const clercId = this.affectations[d.id];
    if (!clercId) return;
    this.erreur.set(null);
    this.api.assigner(d.id, { clercId }).subscribe({
      next: (maj) => {
        this.message.set(`${maj.resume.reference} confie a ${maj.resume.clercNom}.`);
        delete this.affectations[d.id];
        this.charger();
      },
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
      adresse: '',
      commune: '',
      partie: '',
      partieType: 'PHYSIQUE' as 'PHYSIQUE' | 'MORALE',
      partieQualite: 'VENDEUR',
      dossierLieId: '',
    };
  }

}
