/* Modeles alignes sur les vues JSON de l'API Spring Boot (web/dto/Vues.java). */

export type Role =
  | 'NOTAIRE_TITULAIRE'
  | 'NOTAIRE_ASSOCIE'
  | 'NOTAIRE_SALARIE'
  | 'PRINCIPAL_CLERC'
  | 'CLERC'
  | 'COMPTABLE_TAXATEUR'
  | 'FORMALISTE'
  | 'ARCHIVISTE'
  | 'ASSISTANT';

export type StatutDossier =
  | 'DRAFT'
  | 'PENDING_DOCS'
  | 'DRAFTING'
  | 'REVIEW'
  | 'TAXING'
  | 'FUNDING'
  | 'READY_TO_SIGN'
  | 'SIGNED'
  | 'POST_ACT'
  | 'CLOSED';

export type TypeActe =
  | 'VENTE_IMMOBILIERE'
  | 'DROIT_DES_SOCIETES'
  | 'SUCCESSION'
  | 'DONATION_FAMILLE'
  | 'BAIL_COMMERCIAL'
  | 'CREDIT_HYPOTHECAIRE';

export type StatutPiece = 'MANQUANTE' | 'DEMANDEE' | 'RECUE' | 'VALIDEE' | 'REJETEE';
export type ModeSignature = 'PHYSIQUE' | 'ELECTRONIQUE_GAINDE';
export type StatutGainde = 'TRANSMIS' | 'VALIDE' | 'REJETE';
export type NiveauAlerte = 'CRITIQUE' | 'SERIEUX' | 'VIGILANCE' | 'INFO';
export type OrganismeFormalites = 'CONSERVATION_FONCIERE' | 'GREFFE_RCCM' | 'PUBLICATION_LEGALE';
export type TypeRendezVous = 'CONSULTATION' | 'REMISE_PIECES' | 'LECTURE_PROJET' | 'SIGNATURE' | 'REMISE_FONDS' | 'AUTRE';
export type StatutRendezVous = 'PLANIFIE' | 'CONFIRME' | 'HONORE' | 'ANNULE' | 'ABSENT';
export type Salle = 'SALLE_SIGNATURES' | 'BUREAU_NOTAIRE' | 'SALLE_REUNION' | 'BUREAU_CLERC' | 'VISIOCONFERENCE';

export interface Utilisateur {
  id: string;
  prenom: string;
  nom: string;
  nomComplet: string;
  role: Role;
  roleLibelle: string;
  initiales: string;
  actif: boolean;
  notaire: boolean;
  encadrement: boolean;
}

export interface Membre {
  utilisateur: Utilisateur;
  description: string;
  referenceNomination: string | null;
  notaireResponsable: string | null;
  dateEntree: string;
  dossiersRediges: number;
  dossiersSupervises: number;
  rendezVousAVenir: number;
}

export interface RendezVous {
  id: number;
  type: TypeRendezVous;
  typeLibelle: string;
  statut: StatutRendezVous;
  statutLibelle: string;
  debut: string;
  fin: string;
  dureeMinutes: number;
  salle: Salle;
  salleLibelle: string;
  notaireId: string | null;
  notaireNom: string | null;
  collaborateurId: string | null;
  collaborateurNom: string | null;
  dossierId: string | null;
  dossierReference: string | null;
  clientNom: string | null;
  clientTelephone: string | null;
  objet: string | null;
  nombreReports: number;
  motifAnnulation: string | null;
}

export interface ReferentielsAgenda {
  types: { code: TypeRendezVous; libelle: string; dureeParDefaut: number }[];
  salles: { code: Salle; libelle: string; partagee: boolean }[];
  ouverture: string;
  fermeture: string;
  joursOuvres: number[];
}

export interface LigneMinutier {
  numeroRepertoire: string;
  dossierId: string;
  reference: string;
  intitule: string;
  typeActeLibelle: string;
  dateSignature: string | null;
  notaireSignataire: string | null;
  notaireResponsable: string | null;
  dateArchivage: string;
  conservationJusquau: string;
}

export interface Formalite {
  organisme: OrganismeFormalites;
  libelle: string;
  objet: string;
  requise: boolean;
  dateDepot: string | null;
  reference: string | null;
  dateAchevement: string | null;
}

export interface Blocage {
  code: string;
  message: string;
}

export interface ErreurApi {
  message: string;
  blocages: Blocage[];
}

export interface StatutVue {
  code: StatutDossier;
  libelle: string;
  acteur: string;
  description: string;
  rang: number;
  delaiCibleJours: number | null;
}

export interface TransitionVue {
  de: StatutDossier;
  vers: StatutDossier;
  action: string;
  roles: Role[];
  rolesLibelles: string[];
  notaireDuDossier: boolean;
  retour: boolean;
}

export interface WorkflowVue {
  statuts: StatutVue[];
  transitions: TransitionVue[];
}

export interface ActionVue {
  transition: TransitionVue;
  autorisee: boolean;
  blocages: Blocage[];
}

export interface DossierResume {
  id: string;
  reference: string;
  intitule: string;
  typeActe: TypeActe;
  typeActeLibelle: string;
  statut: StatutDossier;
  statutLibelle: string;
  clercId: string | null;
  clercNom: string | null;
  notaireId: string;
  notaireNom: string;
  typeComplexe: boolean;
  dateOuverture: string;
  dateSignaturePrevue: string | null;
  dateSignature: string | null;
  montantTransaction: number;
  tauxCompletudePieces: number;
  soldeFonds: number;
  joursDansStatut: number;
  delaiCibleJours: number | null;
  retardStatutJours: number;
  retardSignatureJours: number;
  alerteCdc: boolean;
  purgeConditionsEnCours: boolean;
  modeSignature: ModeSignature | null;
  gaindeStatut: StatutGainde | null;
}

export interface Bien {
  designation: string;
  titreFoncier: string;
  adresse: string;
  commune: string;
  superficieM2: number;
  usage: string;
}

export interface Intervenant {
  id: number;
  type: 'PHYSIQUE' | 'MORALE';
  nom: string;
  qualite: string;
  identifiant: string | null;
  telephone: string | null;
  email: string | null;
  kycValide: boolean;
}

export interface Piece {
  id: number;
  ordre: number;
  libelle: string;
  categorie: string;
  obligatoire: boolean;
  exigibleA: StatutDossier;
  exigibleALibelle: string;
  validiteJours: number | null;
  responsable: string;
  statut: StatutPiece;
  dateDemande: string | null;
  dateReception: string | null;
  dateEmission: string | null;
  dateExpiration: string | null;
  perimee: boolean;
  commentaire: string | null;
}

export interface LigneDecompte {
  code: string;
  libelle: string;
  categorie: string;
  base: number;
  taux: number | null;
  montant: number;
  acquisEtude: boolean;
  reference: string;
}

export interface DecompteTaxation {
  typeActe: TypeActe;
  assiette: number;
  lignes: LigneDecompte[];
  totaux: {
    droitsEtTaxes: number;
    emoluments: number;
    honoraires: number;
    debours: number;
    tva: number;
    totalGeneral: number;
    produitEtude: number;
    retenueVendeur: number;
  };
  versionBareme: string;
}

export interface CouvertureFonds {
  frais: number;
  prix: number;
  besoin: number;
  encaisse: number;
  manquant: number;
  couvert: boolean;
}

export interface Visa {
  parId: string;
  parNom: string;
  date: string;
}

export interface SousCompte {
  dossierId: string;
  reference: string;
  libelle: string;
  solde: number;
  dateOuverture: string;
  dateDernierMouvement: string;
}

export interface LigneReleve {
  id: number;
  numeroPiece: string;
  dateOperation: string;
  sens: 'CREDIT' | 'DEBIT';
  montant: number;
  nature: string;
  libelle: string;
  contrepartie: string;
  moyenPaiement: string;
  rapproche: boolean;
  soldeCumule: number;
}

export interface Evenement {
  id: number;
  horodatage: string;
  type: string;
  auteurId: string;
  auteurNom: string;
  statutDossier: StatutDossier;
  statutLibelle: string;
  libelle: string;
  detail: Record<string, string>;
}

export interface DossierDetail {
  resume: DossierResume;
  bien: Bien | null;
  intervenants: Intervenant[];
  pieces: Piece[];
  taxation: DecompteTaxation;
  fraisPrevisionnels: number | null;
  versionBareme: string | null;
  couverture: CouvertureFonds;
  validationNotaire: Visa | null;
  certificationFonds: Visa | null;
  montantCertifie: number | null;
  visaPrincipalClerc: Visa | null;
  taxageDefinitif: Visa | null;
  fraisDefinitifs: number | null;
  signature: {
    mode: ModeSignature;
    modeLibelle: string;
    date: string;
    signataireNom: string | null;
    signataireRole: string | null;
    responsableNom: string | null;
    gaindeStatut: StatutGainde | null;
    gaindeStatutLibelle: string | null;
    gaindeDateMiseAJour: string | null;
    gaindeReference: string | null;
  } | null;
  formalites: Formalite[];
  minute: { numeroRepertoire: string; dateArchivage: string; archiveParNom: string; conservationJusquau: string } | null;
  rendezVous: RendezVous[];
  sousCompte: SousCompte | null;
  releve: LigneReleve[];
  actions: ActionVue[];
  journal: Evenement[];
}

export interface NotificationRetard {
  dossierId: string;
  reference: string;
  intitule: string;
  statut: StatutDossier;
  joursDansStatut: number;
  delaiCibleJours: number;
  retardJours: number;
  clercId: string | null;
  notaireId: string;
  niveau: NiveauAlerte;
}

export interface ControleEtancheite {
  soldeBancaire: number;
  sommeAuxiliaires: number;
  ecart: number;
  conforme: boolean;
  sousComptesDebiteurs: SousCompte[];
  dateControle: string;
}

export interface AlerteConformite {
  id: string;
  dossierId: string;
  reference: string;
  intitule: string;
  niveau: NiveauAlerte;
  regle: string;
  message: string;
  montantConcerne: number;
  ancienneteJours: number | null;
  actionSuggeree: string;
  dateDetection: string;
}

export interface TableauDeBord {
  kpi: {
    dossiersActifs: number;
    dossiersSignesCeMois: number;
    encoursFondsClients: number;
    emolumentsAPercevoir: number;
    delaiMoyenJours: number;
    objectifDelaiJours: number;
    tauxRetardGlobal: number;
  };
  pipeline: { statut: StatutDossier; libelle: string; acteur: string; dossiers: number; enRetard: number }[];
  controle: ControleEtancheite;
  alertes: AlerteConformite[];
  notifications: NotificationRetard[];
  charges: {
    userId: string;
    nom: string;
    initiales: string;
    dossiersActifs: number;
    dossiersEnRetard: number;
    tauxRetard: number;
    encoursFonds: number;
  }[];
  caParType: { typeActe: TypeActe; libelle: string; dossiers: number; caPrevisionnel: number }[];
  risques: DossierResume[];
  tauxRetardMaximum: number;
  rendezVousDuJour: RendezVous[];
  dossiersAAssigner: number;
}

export interface VerdictVirement {
  autorise: boolean;
  code: string | null;
  message: string;
  soldeDisponible: number;
  montantRequis: number;
  manquant: number;
}

export interface OrdreVirementCdc {
  reference: string;
  dateEmission: string;
  beneficiaire: string;
  objet: string;
  montant: number;
  dossierReference: string;
  ancienneteJours: number;
  fondementReglementaire: string;
  mentionConformite: string;
}

export const TYPES_ACTE: { code: TypeActe; libelle: string }[] = [
  { code: 'VENTE_IMMOBILIERE', libelle: 'Vente immobiliere' },
  { code: 'DROIT_DES_SOCIETES', libelle: 'Droit des societes' },
  { code: 'SUCCESSION', libelle: 'Succession' },
  { code: 'DONATION_FAMILLE', libelle: 'Donation / Famille' },
  { code: 'BAIL_COMMERCIAL', libelle: 'Bail commercial' },
  { code: 'CREDIT_HYPOTHECAIRE', libelle: 'Credit hypothecaire' },
];
