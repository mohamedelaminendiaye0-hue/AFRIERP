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
  Organisme,
  OrganismeFormalites,
  Piece,
  StatutGainde,
  StatutPiece,
} from '../core/models';
import { SessionService } from '../core/session.service';
import { BadgePieceComponent, BadgeStatutComponent } from '../shared/badges';
import { DateFrPipe, PourcentagePipe, XofPipe } from '../shared/format.pipes';
import { StepperComponent } from '../shared/stepper.component';
import { ConfirmationComponent } from '../shared/confirmation.component';
import { NomIcone } from '../shared/icone.component';
import { formatXof } from '../shared/format.pipes';

const LIBELLE_EVENEMENT: Record<string, string | undefined> = {
  CREATION: 'Création',
  CHANGEMENT_STATUT: 'Changement de statut',
  ASSIGNATION: 'Assignation',
  PIECE_RECUE: 'Pièce reçue',
  PIECE_VALIDEE: 'Validation',
  PIECE_REJETEE: 'Pièce rejetée',
  PIECE_MODIFIEE: 'Modification',
  VALIDATION_NOTAIRE: 'Visa du notaire',
  CERTIFICATION_FONDS: 'Certification des fonds',
  PLANIFICATION_SIGNATURE: 'Rendez-vous de signature',
  RENDEZ_VOUS: 'Rendez-vous',
  VISA_PRINCIPAL_CLERC: 'Visa principal clerc',
  ACHEVEMENT_FORMALITE: 'Formalité achevée',
  TAXAGE_DEFINITIF: 'Taxage définitif',
  ARCHIVAGE_MINUTE: 'Archivage de la minute',
  SIGNATURE: 'Signature',
  SUIVI_GAINDE: 'GAINDE 2000',
  DEPOT_FORMALITES: 'Formalités',
  MOUVEMENT_FONDS: 'Mouvement de fonds',
  TRANSFERT_CDC: 'Transfert CDC',
  EDITION_DOCUMENT: 'Document',
  TENTATIVE_BLOQUEE: 'Tentative bloquée',
};

/** Acte irréversible en attente de confirmation. */
interface ActeAConfirmer {
  titre: string;
  message: string;
  libelle: string;
  recap: [string, string][];
  avertissement: string;
  saisie: string | null;
  icone: NomIcone;
  executer: () => void;
}

@Component({
  selector: 'app-dossier-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, BadgeStatutComponent, BadgePieceComponent, StepperComponent, XofPipe, DateFrPipe, PourcentagePipe, ConfirmationComponent],
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
  protected readonly aConfirmer = signal<ActeAConfirmer | null>(null);

  /* Saisies propres aux actions */
  protected motifs: Record<string, string> = {};
  protected modeSignature: ModeSignature = 'PHYSIQUE';
  protected clercChoisi = '';
  protected notaireChoisi = '';
  protected statutGainde: StatutGainde = 'VALIDE';
  protected referenceGainde = '';
  protected referencesFormalites: Partial<Record<OrganismeFormalites, string>> = {};
  protected nouvellePartie = { nom: '', qualite: 'ACQUEREUR', type: 'PHYSIQUE', identifiant: '' };

  protected readonly libelleEvenement = LIBELLE_EVENEMENT;
  protected readonly statutsPiece: StatutPiece[] = ['MANQUANTE', 'DEMANDEE', 'RECUE', 'VALIDEE', 'REJETEE'];
  protected readonly libellesPiece: Record<StatutPiece, string> = {
    MANQUANTE: 'Manquante', DEMANDEE: 'Demandée', RECUE: 'Reçue', VALIDEE: 'Validée', REJETEE: 'Rejetée',
  };
  protected readonly qualites: [string, string][] = [
    ['VENDEUR', 'Vendeur'], ['ACQUEREUR', 'Acquéreur'], ['DONATEUR', 'Donateur'], ['DONATAIRE', 'Donataire'],
    ['HERITIER', 'Héritier'], ['EMPRUNTEUR', 'Emprunteur'], ['BANQUE', 'Banque'], ['ASSOCIE', 'Associé'],
    ['CEDANT', 'Cédant'], ['CESSIONNAIRE', 'Cessionnaire'], ['MANDANT', 'Mandant'], ['MANDATAIRE', 'Mandataire'], ['SOCIETE', 'Société'],
  ];

  protected libelleCategorie(c: string): string {
    const libelles: Record<string, string> = {
      ETAT_CIVIL: 'état civil', FONCIER: 'foncier', FISCAL: 'fiscal', BANCAIRE: 'bancaire', SOCIETE: 'société',
      URBANISME: 'urbanisme', JUDICIAIRE: 'judiciaire',
    };
    return libelles[c] ?? c.toLowerCase().replace(/_/g, ' ');
  }

  protected readonly clercs = computed(() =>
    this.session.utilisateurs().filter((u) => (u.role === 'CLERC' || u.role === 'PRINCIPAL_CLERC') && u.actif),
  );
  protected readonly notaires = computed(() => this.session.utilisateurs().filter((u) => u.notaire && u.actif));

  /** L'archivage (clôture) se fait uniquement depuis le répertoire des actes. */
  protected readonly avancees = computed(() =>
    this.dossier()?.actions.filter((a) => !a.transition.retour && a.transition.vers !== 'CLOSED') ?? [],
  );
  protected readonly archivageAuRepertoire = computed(() =>
    this.dossier()?.actions.some((a) => a.transition.vers === 'CLOSED') ?? false,
  );
  protected readonly retours = computed(() => this.dossier()?.actions.filter((a) => a.transition.retour) ?? []);

  protected readonly piecesBloquantes = computed(() => {
    const d = this.dossier();
    if (!d) return [];
    return d.pieces.filter((p) => p.obligatoire && (!['RECUE', 'VALIDEE'].includes(p.statut) || p.perimee));
  });

  /** Formalites hors catalogue du type d'acte (ex : homologation judiciaire d'un changement de regime). */
  protected readonly organismes = signal<Organisme[]>([]);
  protected organismeComplementaire = '';
  protected readonly organismesComplementaires = computed(() => {
    const presents = new Set((this.dossier()?.formalites ?? []).map((f) => f.organisme));
    return this.organismes().filter((o) => !presents.has(o.code));
  });

  protected readonly signaturePlanifiee = computed(() =>
    (this.dossier()?.rendezVous ?? []).some((r) => r.type === 'SIGNATURE' && (r.statut === 'PLANIFIE' || r.statut === 'CONFIRME')),
  );

  protected readonly tauxCouverture = computed(() => {
    const c = this.dossier()?.couverture;
    return c && c.besoin > 0 ? Math.min(1, c.encaisse / c.besoin) : 1;
  });

  ngOnInit(): void {
    this.api.organismes().subscribe((o) => this.organismes.set(o));
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
    const lancer = () => {
      this.executer(
        this.api.transition(this.id(), vers, this.motifs[vers], vers === 'SIGNED' ? this.modeSignature : undefined),
        `${a.transition.action} : effectué.`,
      );
      this.motifs[vers] = '';
    };
    const confirmation = this.confirmationPour(a);
    if (confirmation) this.aConfirmer.set({ ...confirmation, executer: lancer });
    else lancer();
  }

  /**
   * Les actes qui engagent l'étude sans retour possible passent par une
   * confirmation : certification des fonds, signature, clôture et archivage.
   */
  private confirmationPour(a: ActionVue): Omit<ActeAConfirmer, 'executer'> | null {
    const d = this.dossier();
    if (!d || a.transition.retour) return null;
    const dossier: [string, string] = ['Dossier', `${d.resume.id} · ${d.resume.intitule}`];
    switch (a.transition.vers) {
      case 'READY_TO_SIGN':
        return {
          titre: 'Certifier les fonds du dossier ?', libelle: 'Certifier les fonds', icone: 'fonds2', saisie: null,
          message: 'Vous attestez que les fonds nécessaires à la signature sont encaissés sur le compte séquestre.',
          recap: [dossier, ['Fonds requis', formatXof(d.couverture.besoin)], ['Fonds encaissés', formatXof(d.couverture.encaisse)]],
          avertissement: 'La certification est tracée à votre nom au journal du dossier.',
        };
      case 'SIGNED':
        return {
          titre: "Signer l'acte et l'inscrire au répertoire ?", libelle: 'Signer et inscrire au répertoire', icone: 'signer',
          message: "L'acte devient une minute authentique. Il ne pourra plus être modifié.",
          recap: [dossier, ['Notaire signataire', this.session.utilisateur()?.nomComplet ?? ''],
            ['Mode de signature', this.modeSignature === 'PHYSIQUE' ? 'Signature manuscrite' : 'Électronique (GAINDE 2000)'],
            ['Fonds certifiés', d.certificationFonds ? `le ${new Date(d.certificationFonds.date).toLocaleDateString('fr-FR')}` : '—']],
          avertissement: 'Un numéro d’ordre définitif est attribué au répertoire ; toute correction passera par un acte rectificatif.',
          saisie: d.resume.id,
        };
      case 'CLOSED':
        return {
          titre: 'Archiver la minute et clôturer le dossier ?', libelle: 'Archiver et clôturer', icone: 'archiver', saisie: null,
          message: 'Plus aucune pièce, partie ni écriture ne pourra être ajoutée au dossier.',
          recap: [dossier, ['Répertoire', d.minute?.numeroRepertoire ?? '—']],
          avertissement: 'La clôture d’un dossier ne peut pas être annulée.',
        };
      default:
        return null;
    }
  }

  protected confirmer(): void {
    const c = this.aConfirmer();
    this.aConfirmer.set(null);
    c?.executer();
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
    this.executer(this.api.majPiece(this.id(), p.id, 'VALIDEE', aujourdhui), `${p.libelle} : pièce renouvelée.`);
  }

  protected validerKyc(intervenantId: number): void {
    this.executer(this.api.validerKyc(this.id(), intervenantId), 'Contrôle KYC / LAB-FT validé.');
  }

  protected ajouterPartie(): void {
    const p = this.nouvellePartie;
    this.executer(
      this.api.ajouterIntervenant(this.id(), { nom: p.nom, qualite: p.qualite, type: p.type, identifiant: p.identifiant || undefined }),
      'Partie ajoutée.',
    );
    this.nouvellePartie = { nom: '', qualite: 'ACQUEREUR', type: 'PHYSIQUE', identifiant: '' };
  }

  protected leverPurge(): void {
    this.executer(this.api.leverPurge(this.id()), 'Purge achevée.');
  }

  protected assigner(): void {
    this.executer(
      this.api.assigner(this.id(), { clercId: this.clercChoisi || null, notaireId: this.notaireChoisi || null }),
      'Assignation enregistrée.',
    );
    this.clercChoisi = '';
    this.notaireChoisi = '';
  }

  protected viser(): void {
    this.executer(this.api.viserProjet(this.id()), 'Projet visé par le principal clerc.');
  }

  protected taxageDefinitif(): void {
    const d = this.dossier();
    this.aConfirmer.set({
      titre: 'Arrêter le taxage définitif ?', libelle: 'Arrêter le taxage', icone: 'calcul', saisie: null,
      message: 'Les frais réels du dossier sont figés : ils servent au solde du compte client et à la facture.',
      recap: [['Dossier', `${d?.resume.id} · ${d?.resume.intitule}`]],
      avertissement: 'Le taxage définitif ne peut plus être modifié une fois arrêté.',
      executer: () => this.executer(this.api.taxageDefinitif(this.id()), 'Taxage définitif arrêté.'),
    });
  }

  protected deposer(organisme: OrganismeFormalites): void {
    this.executer(this.api.deposerFormalite(this.id(), organisme, this.referencesFormalites[organisme]), 'Formalité déposée.');
  }

  protected achever(organisme: OrganismeFormalites): void {
    this.executer(this.api.acheverFormalite(this.id(), organisme), 'Formalité achevée.');
  }

  protected majGainde(): void {
    this.executer(this.api.majGainde(this.id(), this.statutGainde, this.referenceGainde), 'Statut GAINDE 2000 mis à jour.');
  }



  protected entreesDetail(detail: Record<string, string>): [string, string][] {
    return Object.entries(detail ?? {});
  }
}
