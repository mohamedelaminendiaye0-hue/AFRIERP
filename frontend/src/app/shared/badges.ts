import { Component, computed, input } from '@angular/core';
import { NiveauAlerte, StatutDossier, StatutPiece } from '../core/models';

const LIBELLES_STATUT: Record<StatutDossier, string> = {
  DRAFT: 'Creation / Brouillon',
  PENDING_DOCS: 'Instruction & Pieces',
  DRAFTING: 'Redaction en cours',
  REVIEW: 'Validation Titulaire',
  TAXING: 'Taxage previsionnel',
  FUNDING: 'En attente de provision',
  READY_TO_SIGN: 'Pret pour signature',
  SIGNED: 'Signe (minute etablie)',
  POST_ACT: 'Formalites en cours',
  CLOSED: 'Cloture & archive',
};

/**
 * Les couleurs de statut sont reservees : elles accompagnent toujours un
 * libelle, jamais seules. Les etapes ou le dossier attend un tiers (pieces,
 * fonds) sont en vigilance ; les etapes franchies cote signature en vert.
 */
const TON_STATUT: Record<StatutDossier, string> = {
  DRAFT: 'neutre-clair',
  PENDING_DOCS: 'vigilance',
  DRAFTING: 'neutre',
  REVIEW: 'neutre',
  TAXING: 'neutre',
  FUNDING: 'vigilance',
  READY_TO_SIGN: 'bon',
  SIGNED: 'bon',
  POST_ACT: 'neutre',
  CLOSED: 'neutre-clair',
};

export function libelleStatut(statut: StatutDossier): string {
  return LIBELLES_STATUT[statut];
}

@Component({
  selector: 'app-badge-statut',
  standalone: true,
  template: `<span class="badge" [class]="'badge badge--' + ton()">{{ libelle() }}</span>`,
})
export class BadgeStatutComponent {
  readonly statut = input.required<StatutDossier>();
  protected readonly ton = computed(() => TON_STATUT[this.statut()]);
  protected readonly libelle = computed(() => LIBELLES_STATUT[this.statut()]);
}

const PIECE: Record<StatutPiece, { libelle: string; ton: string }> = {
  MANQUANTE: { libelle: 'Manquante', ton: 'critique' },
  DEMANDEE: { libelle: 'Demandee', ton: 'vigilance' },
  RECUE: { libelle: 'Recue', ton: 'neutre' },
  VALIDEE: { libelle: 'Validee', ton: 'bon' },
  REJETEE: { libelle: 'Rejetee', ton: 'critique' },
};

@Component({
  selector: 'app-badge-piece',
  standalone: true,
  template: `<span [class]="'badge badge--petit badge--' + PIECE[statut()].ton">{{ PIECE[statut()].libelle }}</span>`,
})
export class BadgePieceComponent {
  readonly statut = input.required<StatutPiece>();
  protected readonly PIECE = PIECE;
}

const NIVEAU: Record<NiveauAlerte, { libelle: string; ton: string }> = {
  CRITIQUE: { libelle: 'Critique', ton: 'critique' },
  SERIEUX: { libelle: 'Serieux', ton: 'serieux' },
  VIGILANCE: { libelle: 'Vigilance', ton: 'vigilance' },
  INFO: { libelle: 'Information', ton: 'neutre' },
};

@Component({
  selector: 'app-badge-niveau',
  standalone: true,
  template: `<span [class]="'badge badge--' + NIVEAU[niveau()].ton">⚠ {{ NIVEAU[niveau()].libelle }}</span>`,
})
export class BadgeNiveauComponent {
  readonly niveau = input.required<NiveauAlerte>();
  protected readonly NIVEAU = NIVEAU;
}
