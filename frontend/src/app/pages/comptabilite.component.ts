import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { ApiService, lireErreur } from '../core/api.service';
import { AlerteConformite, ControleEtancheite, ErreurApi, LigneReleve, OrdreVirementCdc, SousCompte, VerdictVirement } from '../core/models';
import { SessionService } from '../core/session.service';
import { BadgeNiveauComponent } from '../shared/badges';
import { DateFrPipe, XofPipe } from '../shared/format.pipes';

const NATURES_DECAISSEMENT = [
  { code: 'PAIEMENT_TRESOR', libelle: 'Paiement Trésor (droits, conservation)' },
  { code: 'PAIEMENT_TIERS', libelle: 'Paiement tiers (créancier, géomètre…)' },
  { code: 'VERSEMENT_VENDEUR', libelle: 'Versement du prix au vendeur / cedant' },
  { code: 'DEBLOCAGE_CAPITAL', libelle: 'Libération du capital à la société immatriculée' },
  { code: 'HONORAIRES_ETUDE', libelle: 'Prélèvement des émoluments' },
  { code: 'RESTITUTION_CLIENT', libelle: 'Restitution du reliquat au client' },
  { code: 'TRANSFERT_CDC', libelle: 'Transfert à la CDC' },
];

/**
 * Hard constraint : le bouton de virement n'est jamais « cliquable puis refuse ».
 * Il reste desactive tant que l'API n'a pas rendu un verdict positif.
 */
@Component({
  selector: 'app-comptabilite',
  standalone: true,
  imports: [FormsModule, RouterLink, BadgeNiveauComponent, XofPipe, DateFrPipe],
  templateUrl: './comptabilite.component.html',
})
export class ComptabiliteComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);

  protected readonly controle = signal<ControleEtancheite | null>(null);
  protected readonly sousComptes = signal<SousCompte[]>([]);
  protected readonly alertes = signal<AlerteConformite[]>([]);
  protected readonly releve = signal<LigneReleve[]>([]);
  protected readonly ordre = signal<OrdreVirementCdc | null>(null);
  protected readonly verdict = signal<VerdictVirement | null>(null);
  protected readonly erreur = signal<ErreurApi | null>(null);
  protected readonly confirmation = signal<string | null>(null);

  protected readonly natures = NATURES_DECAISSEMENT;
  protected dossierId = '';
  protected montant = 0;
  protected nature = 'PAIEMENT_TIERS';
  protected beneficiaire = '';

  private readonly verification$ = new Subject<void>();

  protected readonly ratio = computed(() => {
    const v = this.verdict();
    return v && v.soldeDisponible > 0 ? Math.min(1, v.montantRequis / v.soldeDisponible) : 0;
  });

  ngOnInit(): void {
    this.charger();
    this.verification$
      .pipe(
        debounceTime(200),
        switchMap(() => this.api.verifierVirement({ dossierId: this.dossierId, montant: Number(this.montant) || 0, nature: this.nature })),
      )
      .subscribe((v) => this.verdict.set(v));
  }

  protected choisirSousCompte(id: string): void {
    this.dossierId = id;
    this.api.releve(id).subscribe((r) => this.releve.set(r));
    this.verifier();
  }

  protected verifier(): void {
    this.confirmation.set(null);
    this.erreur.set(null);
    if (this.dossierId) this.verification$.next();
  }

  protected emettre(): void {
    this.api
      .executerVirement({ dossierId: this.dossierId, montant: Number(this.montant), nature: this.nature, beneficiaire: this.beneficiaire })
      .subscribe({
        next: (l) => {
          this.confirmation.set(`Ordre émis : pièce ${l.numeroPiece}, ${l.libelle}.`);
          this.montant = 0;
          this.charger();
          this.choisirSousCompte(this.dossierId);
        },
        error: (e) => this.erreur.set(lireErreur(e)),
      });
  }

  protected genererOrdre(a: AlerteConformite): void {
    this.api.ordreCdc(a.dossierId).subscribe({ next: (o) => this.ordre.set(o), error: (e) => this.erreur.set(lireErreur(e)) });
  }

  protected imprimer(): void {
    window.print();
  }

  private charger(): void {
    this.api.controle().subscribe((c) => this.controle.set(c));
    this.api.alertes().subscribe((a) => this.alertes.set(a));
    this.api.sousComptes().subscribe((s) => {
      this.sousComptes.set([...s].sort((a, b) => b.solde - a.solde));
      if (!this.dossierId && s.length) this.choisirSousCompte(s[0].dossierId);
    });
  }
}
