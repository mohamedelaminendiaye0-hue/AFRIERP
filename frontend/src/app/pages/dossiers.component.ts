import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService, NouveauDossier, lireErreur } from '../core/api.service';
import { DossierResume, ErreurApi, TYPES_ACTE, TypeActe } from '../core/models';
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
  protected readonly typesActe = TYPES_ACTE;

  protected readonly filtres: Filtre[] = [
    { cle: 'tous', libelle: 'Tous', predicat: () => true },
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
    return this.dossiers()
      .filter(filtre.predicat)
      .filter((d) => !q || `${d.reference} ${d.intitule} ${d.clercNom} ${d.typeActeLibelle}`.toLowerCase().includes(q));
  });

  protected readonly clercs = computed(() => this.session.utilisateurs().filter((u) => u.role === 'CLERC' && u.actif));
  protected readonly notaires = computed(() =>
    this.session.utilisateurs().filter((u) => (u.role === 'NOTAIRE' || u.role === 'DIRECTEUR') && u.actif),
  );

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
      clercId: n.clercId,
      notaireId: n.notaireId,
      bien: n.designation ? { designation: n.designation, titreFoncier: n.titreFoncier, adresse: n.adresse, commune: n.commune } : null,
      intervenants: n.partie ? [{ type: n.partieType, nom: n.partie, qualite: n.partieQualite }] : [],
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
      clercId: '',
      notaireId: '',
      designation: '',
      titreFoncier: '',
      adresse: '',
      commune: '',
      partie: '',
      partieType: 'PHYSIQUE' as 'PHYSIQUE' | 'MORALE',
      partieQualite: 'VENDEUR',
    };
  }

}
