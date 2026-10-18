import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService, lireErreur } from '../core/api.service';
import {
  ActionVue,
  DossierDetail,
  ErreurApi,
  ModeSignature,
  OrganismeFormalites,
  Piece,
  StatutGainde,
  StatutPiece,
} from '../core/models';
import { SessionService } from '../core/session.service';
import { BadgePieceComponent, BadgeStatutComponent } from '../shared/badges';
import { DateFrPipe, PourcentagePipe, XofPipe } from '../shared/format.pipes';
import { StepperComponent } from '../shared/stepper.component';

const LIBELLE_EVENEMENT: Record<string, string | undefined> = {
  CREATION: 'Creation',
  CHANGEMENT_STATUT: 'Changement de statut',
  ASSIGNATION: 'Assignation',
  PIECE_RECUE: 'Piece recue',
  PIECE_VALIDEE: 'Validation',
  PIECE_REJETEE: 'Piece rejetee',
  PIECE_MODIFIEE: 'Modification',
  VALIDATION_NOTAIRE: 'Visa du notaire',
  CERTIFICATION_FONDS: 'Certification des fonds',
  PLANIFICATION_SIGNATURE: 'Rendez-vous de signature',
  SIGNATURE: 'Signature',
  SUIVI_GAINDE: 'GAINDE 2000',
  DEPOT_FORMALITES: 'Formalites',
  MOUVEMENT_FONDS: 'Mouvement de fonds',
  TRANSFERT_CDC: 'Transfert CDC',
  EDITION_DOCUMENT: 'Document',
  TENTATIVE_BLOQUEE: 'Tentative bloquee',
};

@Component({
  selector: 'app-dossier-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, BadgeStatutComponent, BadgePieceComponent, StepperComponent, XofPipe, DateFrPipe, PourcentagePipe],
  templateUrl: './dossier-detail.component.html',
  styleUrl: './dossier-detail.component.scss',
})
export class DossierDetailComponent implements OnInit {
  /** Parametre de route `:id` (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);

  protected readonly dossier = signal<DossierDetail | null>(null);
  protected readonly erreur = signal<ErreurApi | null>(null);
  protected readonly succes = signal<string | null>(null);
  protected readonly enCours = signal(false);

  /* Saisies propres aux actions */
  protected motifs: Record<string, string> = {};
  protected modeSignature: ModeSignature = 'PHYSIQUE';
  protected dateRdv = '';
  protected clercChoisi = '';
  protected statutGainde: StatutGainde = 'VALIDE';
  protected referenceGainde = '';
  protected organisme: OrganismeFormalites = 'CONSERVATION_FONCIERE';
  protected referenceFormalites = '';
  protected nouvellePartie = { nom: '', qualite: 'ACQUEREUR', type: 'PHYSIQUE', identifiant: '' };

  protected readonly libelleEvenement = LIBELLE_EVENEMENT;
  protected readonly statutsPiece: StatutPiece[] = ['MANQUANTE', 'DEMANDEE', 'RECUE', 'VALIDEE', 'REJETEE'];

  protected readonly clercs = computed(() => this.session.utilisateurs().filter((u) => u.role === 'CLERC' && u.actif));

  protected readonly avancees = computed(() => this.dossier()?.actions.filter((a) => !a.transition.retour) ?? []);
  protected readonly retours = computed(() => this.dossier()?.actions.filter((a) => a.transition.retour) ?? []);

  protected readonly piecesBloquantes = computed(() => {
    const d = this.dossier();
    if (!d) return [];
    return d.pieces.filter((p) => p.obligatoire && (!['RECUE', 'VALIDEE'].includes(p.statut) || p.perimee));
  });

  protected readonly tauxCouverture = computed(() => {
    const c = this.dossier()?.couverture;
    return c && c.besoin > 0 ? Math.min(1, c.encaisse / c.besoin) : 1;
  });

  ngOnInit(): void {
    this.api.dossier(this.id()).subscribe({
      next: (d) => this.dossier.set(d),
      error: (e) => this.erreur.set(lireErreur(e)),
    });
  }

  /* ------------------------------------------------------------------ *
   *  Actions
   * ------------------------------------------------------------------ */

  private executer(requete: Observable<DossierDetail>, message: string): void {
    this.erreur.set(null);
    this.succes.set(null);
    this.enCours.set(true);
    requete.subscribe({
      next: (d) => {
        this.dossier.set(d);
        this.succes.set(message);
        this.enCours.set(false);
      },
      error: (e) => {
        this.erreur.set(lireErreur(e));
        this.enCours.set(false);
        // Recharge : la tentative refusee est desormais tracee au journal.
        this.api.dossier(this.id()).subscribe((d) => this.dossier.set(d));
      },
    });
  }

  protected transition(a: ActionVue): void {
    const vers = a.transition.vers;
    this.executer(
      this.api.transition(this.id(), vers, this.motifs[vers], vers === 'SIGNED' ? this.modeSignature : undefined),
      `${a.transition.action} : effectue.`,
    );
    this.motifs[vers] = '';
  }

  protected actionPossible(a: ActionVue): boolean {
    if (!a.autorisee || this.enCours()) return false;
    return !a.transition.retour || !!this.motifs[a.transition.vers]?.trim();
  }

  protected changerPiece(p: Piece, statut: StatutPiece): void {
    this.executer(this.api.majPiece(this.id(), p.id, statut), `${p.libelle} : ${statut.toLowerCase()}.`);
  }

  protected renouvelerPiece(p: Piece): void {
    const aujourdhui = new Date().toISOString().slice(0, 10);
    this.executer(this.api.majPiece(this.id(), p.id, 'VALIDEE', aujourdhui), `${p.libelle} : piece renouvelee.`);
  }

  protected validerKyc(intervenantId: number): void {
    this.executer(this.api.validerKyc(this.id(), intervenantId), 'Controle KYC / LAB-FT valide.');
  }

  protected ajouterPartie(): void {
    const p = this.nouvellePartie;
    this.executer(
      this.api.ajouterIntervenant(this.id(), { nom: p.nom, qualite: p.qualite, type: p.type, identifiant: p.identifiant || undefined }),
      'Partie ajoutee.',
    );
    this.nouvellePartie = { nom: '', qualite: 'ACQUEREUR', type: 'PHYSIQUE', identifiant: '' };
  }

  protected leverPurge(): void {
    this.executer(this.api.leverPurge(this.id()), 'Purge achevee.');
  }

  protected assigner(): void {
    this.executer(this.api.assigner(this.id(), this.clercChoisi), 'Dossier reassigne.');
  }

  protected planifier(): void {
    this.executer(this.api.planifierSignature(this.id(), this.dateRdv), 'Rendez-vous de signature planifie.');
  }

  protected majGainde(): void {
    this.executer(this.api.majGainde(this.id(), this.statutGainde, this.referenceGainde), 'Statut GAINDE 2000 mis a jour.');
  }

  protected deposerFormalites(): void {
    this.executer(this.api.deposerFormalites(this.id(), this.organisme, this.referenceFormalites), 'Depot des formalites enregistre.');
  }

  protected entreesDetail(detail: Record<string, string>): [string, string][] {
    return Object.entries(detail ?? {});
  }
}
