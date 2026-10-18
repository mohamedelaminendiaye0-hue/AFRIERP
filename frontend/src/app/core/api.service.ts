import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AlerteConformite,
  ControleEtancheite,
  DecompteTaxation,
  DossierDetail,
  DossierResume,
  ErreurApi,
  LigneReleve,
  ModeSignature,
  NotificationRetard,
  OrdreVirementCdc,
  OrganismeFormalites,
  SousCompte,
  StatutDossier,
  StatutGainde,
  StatutPiece,
  TableauDeBord,
  TypeActe,
  Utilisateur,
  VerdictVirement,
  WorkflowVue,
} from './models';

export interface NouveauDossier {
  intitule: string;
  typeActe: TypeActe;
  montantTransaction: number;
  clercId: string;
  notaireId: string;
  bien?: { designation: string; titreFoncier: string; adresse: string; commune: string } | null;
  intervenants: { type: 'PHYSIQUE' | 'MORALE'; nom: string; qualite: string; identifiant?: string }[];
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  utilisateurs(): Observable<Utilisateur[]> {
    return this.http.get<Utilisateur[]>('/api/utilisateurs');
  }

  workflow(): Observable<WorkflowVue> {
    return this.http.get<WorkflowVue>('/api/workflow');
  }

  tableauDeBord(): Observable<TableauDeBord> {
    return this.http.get<TableauDeBord>('/api/dashboard');
  }

  notifications(): Observable<NotificationRetard[]> {
    return this.http.get<NotificationRetard[]>('/api/notifications');
  }

  /* --- Dossiers --- */

  dossiers(): Observable<DossierResume[]> {
    return this.http.get<DossierResume[]>('/api/dossiers');
  }

  dossier(id: string): Observable<DossierDetail> {
    return this.http.get<DossierDetail>(`/api/dossiers/${id}`);
  }

  creerDossier(cmd: NouveauDossier): Observable<DossierDetail> {
    return this.http.post<DossierDetail>('/api/dossiers', cmd);
  }

  transition(id: string, vers: StatutDossier, motif?: string, modeSignature?: ModeSignature): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/transitions`, { vers, motif, modeSignature });
  }

  majPiece(id: string, pieceId: number, statut: StatutPiece, dateEmission?: string): Observable<DossierDetail> {
    return this.http.patch<DossierDetail>(`/api/dossiers/${id}/pieces/${pieceId}`, { statut, dateEmission });
  }

  ajouterIntervenant(id: string, cmd: { type: string; nom: string; qualite: string; identifiant?: string }): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/intervenants`, cmd);
  }

  validerKyc(id: string, intervenantId: number): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/intervenants/${intervenantId}/kyc`, {});
  }

  leverPurge(id: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/purge/levee`, {});
  }

  assigner(id: string, clercId: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/assignation`, { clercId });
  }

  planifierSignature(id: string, date: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/rendez-vous-signature`, { date });
  }

  majGainde(id: string, statut: StatutGainde, reference?: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/gainde`, { statut, reference });
  }

  deposerFormalites(id: string, organisme: OrganismeFormalites, reference?: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/formalites`, { organisme, reference });
  }

  /* --- Comptabilite --- */

  controle(): Observable<ControleEtancheite> {
    return this.http.get<ControleEtancheite>('/api/compta/controle');
  }

  sousComptes(): Observable<SousCompte[]> {
    return this.http.get<SousCompte[]>('/api/compta/sous-comptes');
  }

  alertes(): Observable<AlerteConformite[]> {
    return this.http.get<AlerteConformite[]>('/api/compta/alertes');
  }

  releve(dossierId: string): Observable<LigneReleve[]> {
    return this.http.get<LigneReleve[]>(`/api/compta/sous-comptes/${dossierId}/releve`);
  }

  verifierVirement(cmd: { dossierId: string; montant: number; nature: string }): Observable<VerdictVirement> {
    return this.http.post<VerdictVirement>('/api/compta/virements/verification', cmd);
  }

  executerVirement(cmd: { dossierId: string; montant: number; nature: string; beneficiaire: string }): Observable<LigneReleve> {
    return this.http.post<LigneReleve>('/api/compta/virements', cmd);
  }

  ordreCdc(dossierId: string): Observable<OrdreVirementCdc> {
    return this.http.get<OrdreVirementCdc>(`/api/compta/ordres-cdc/${dossierId}`);
  }

  /* --- Simulateur --- */

  simuler(typeActe: TypeActe, assiette: number, honoraireLibre: number): Observable<DecompteTaxation> {
    return this.http.post<DecompteTaxation>('/api/taxation/simulation', { typeActe, assiette, honoraireLibre });
  }
}

/** Extrait les motifs metier d'une reponse d'erreur de l'API. */
export function lireErreur(e: unknown): ErreurApi {
  if (e instanceof HttpErrorResponse && e.error && typeof e.error === 'object' && 'message' in e.error) {
    return e.error as ErreurApi;
  }
  return { message: "Le serveur n'a pas pu traiter la demande.", blocages: [] };
}
