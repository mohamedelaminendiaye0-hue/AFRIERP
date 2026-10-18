import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AlerteConformite,
  Blocage,
  Client,
  ClientResume,
  CompteDemo,
  FicheClient,
  IdentiteEtude,
  LigneMinutier,
  Repertoire,
  ResultatArchivage,
  SaisieClient,
  Session,
  Organisme,
  TypeActeVue,
  Membre,
  ReferentielsAgenda,
  RendezVous,
  Salle,
  StatutRendezVous,
  TypeRendezVous,
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
  prixAcquisition?: number | null;
  clercId?: string | null;
  notaireId: string;
  dossierLieId?: string | null;
  bien?: { designation: string; titreFoncier: string; adresse: string; commune: string } | null;
  intervenants: { type?: 'PHYSIQUE' | 'MORALE'; nom?: string; qualite: string; identifiant?: string; clientId?: string }[];
}

export interface NouveauRendezVous {
  type: TypeRendezVous;
  debut: string;
  dureeMinutes: number;
  salle: Salle;
  notaireId?: string | null;
  collaborateurId?: string | null;
  dossierId?: string | null;
  clientNom?: string | null;
  clientTelephone?: string | null;
  objet?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  typesActe(): Observable<TypeActeVue[]> {
    return this.http.get<TypeActeVue[]>('/api/referentiels/types-acte');
  }

  organismes(): Observable<Organisme[]> {
    return this.http.get<Organisme[]>('/api/referentiels/organismes');
  }

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

  ajouterIntervenant(id: string, cmd: { type?: string; nom?: string; qualite: string; identifiant?: string; clientId?: string }): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/intervenants`, cmd);
  }

  validerKyc(id: string, intervenantId: number): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/intervenants/${intervenantId}/kyc`, {});
  }

  leverPurge(id: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/purge/levee`, {});
  }

  assigner(id: string, cmd: { clercId?: string | null; notaireId?: string | null }): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/assignation`, cmd);
  }

  viserProjet(id: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/visa-principal-clerc`, {});
  }

  taxageDefinitif(id: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/taxage-definitif`, {});
  }

  majGainde(id: string, statut: StatutGainde, reference?: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/gainde`, { statut, reference });
  }

  deposerFormalite(id: string, organisme: OrganismeFormalites, reference?: string): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/formalites`, { organisme, reference });
  }

  acheverFormalite(id: string, organisme: OrganismeFormalites): Observable<DossierDetail> {
    return this.http.post<DossierDetail>(`/api/dossiers/${id}/formalites/${organisme}/achevement`, {});
  }

  /* --- Equipe & minutier --- */

  equipe(): Observable<Membre[]> {
    return this.http.get<Membre[]>('/api/equipe');
  }

  minutier(): Observable<LigneMinutier[]> {
    return this.http.get<LigneMinutier[]>('/api/minutier');
  }

  /* --- Rendez-vous --- */

  referentielsAgenda(): Observable<ReferentielsAgenda> {
    return this.http.get<ReferentielsAgenda>('/api/rendez-vous/referentiels');
  }

  agenda(du: string, au: string): Observable<RendezVous[]> {
    return this.http.get<RendezVous[]>('/api/rendez-vous', { params: { du, au } });
  }

  rendezVousDuJour(): Observable<RendezVous[]> {
    return this.http.get<RendezVous[]>('/api/rendez-vous/aujourdhui');
  }

  verifierRendezVous(cmd: NouveauRendezVous): Observable<Blocage[]> {
    return this.http.post<Blocage[]>('/api/rendez-vous/verification', cmd);
  }

  creerRendezVous(cmd: NouveauRendezVous): Observable<RendezVous> {
    return this.http.post<RendezVous>('/api/rendez-vous', cmd);
  }

  reporterRendezVous(id: number, debut: string, dureeMinutes?: number): Observable<RendezVous> {
    return this.http.post<RendezVous>(`/api/rendez-vous/${id}/report`, { debut, dureeMinutes });
  }

  statutRendezVous(id: number, statut: StatutRendezVous, motif?: string): Observable<RendezVous> {
    return this.http.post<RendezVous>(`/api/rendez-vous/${id}/statut`, { statut, motif });
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

  simuler(typeActe: TypeActe, assiette: number, honoraireLibre: number, prixAcquisition?: number | null): Observable<DecompteTaxation> {
    return this.http.post<DecompteTaxation>('/api/taxation/simulation', { typeActe, assiette, honoraireLibre, prixAcquisition });
  }

  /* --- Session et identité de l'étude --- */

  connexion(email: string, motDePasse: string): Observable<Session> {
    return this.http.post<Session>('/api/auth/connexion', { email, motDePasse });
  }

  comptesDemo(): Observable<CompteDemo[]> {
    return this.http.get<CompteDemo[]>('/api/auth/comptes-demo');
  }

  moi(): Observable<Utilisateur> {
    return this.http.get<Utilisateur>('/api/moi');
  }

  identite(): Observable<IdentiteEtude> {
    return this.http.get<IdentiteEtude>('/api/identite');
  }

  enregistrerIdentite(identite: IdentiteEtude): Observable<IdentiteEtude> {
    return this.http.put<IdentiteEtude>('/api/identite', identite);
  }

  /* --- Clients --- */

  clients(q?: string): Observable<ClientResume[]> {
    return this.http.get<ClientResume[]>('/api/clients', { params: q ? { q } : {} });
  }

  ficheClient(id: string): Observable<FicheClient> {
    return this.http.get<FicheClient>(`/api/clients/${id}`);
  }

  creerClient(saisie: SaisieClient): Observable<Client> {
    return this.http.post<Client>('/api/clients', saisie);
  }

  modifierClient(id: string, saisie: SaisieClient): Observable<Client> {
    return this.http.put<Client>(`/api/clients/${id}`, saisie);
  }

  /* --- Répertoire et archivage --- */

  repertoire(annee?: number): Observable<Repertoire> {
    return this.http.get<Repertoire>('/api/repertoire', { params: annee ? { annee } : {} });
  }

  archiver(dossierIds: string[], emplacement?: string | null): Observable<ResultatArchivage[]> {
    return this.http.post<ResultatArchivage[]>('/api/minutier/archivage', { dossierIds, emplacement });
  }
}

/** Extrait les motifs metier d'une reponse d'erreur de l'API. */
export function lireErreur(e: unknown): ErreurApi {
  if (e instanceof HttpErrorResponse && e.error && typeof e.error === 'object' && 'message' in e.error) {
    return e.error as ErreurApi;
  }
  return { message: "Le serveur n'a pas pu traiter la demande.", blocages: [] };
}
