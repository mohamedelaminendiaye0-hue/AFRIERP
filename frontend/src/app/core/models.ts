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
  | 'ASSISTANT'
  | 'SUPER_ADMIN';

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

/** Code d'un type d'acte du catalogue (voir /api/referentiels/types-acte). */
export type TypeActe = string;
export type DomaineActe = 'IMMOBILIER' | 'AFFAIRES' | 'FAMILLE' | 'AUTHENTIFICATION';

export type StatutPiece = 'MANQUANTE' | 'DEMANDEE' | 'RECUE' | 'VALIDEE' | 'REJETEE';
export type ModeSignature = 'PHYSIQUE' | 'ELECTRONIQUE_GAINDE';
export type StatutGainde = 'TRANSMIS' | 'VALIDE' | 'REJETE';
export type NiveauAlerte = 'CRITIQUE' | 'SERIEUX' | 'VIGILANCE' | 'INFO';
export type OrganismeFormalites = 'DGID' | 'CONSERVATION_FONCIERE' | 'GREFFE_RCCM' | 'NINEA' | 'PUBLICATION_LEGALE' | 'TRIBUNAL';
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
  email: string;
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
  dateArchivage: string | null;
  conservationJusquau: string | null;
  etat: 'FORMALITES' | 'A_ARCHIVER' | 'ARCHIVEE';
  emplacement: string | null;
  blocages: string[];
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
  domaine: DomaineActe;
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
  qualiteLibelle: string;
  identifiant: string | null;
  telephone: string | null;
  email: string | null;
  kycValide: boolean;
  clientId: string | null;
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
  dossierLie: { id: string; reference: string; intitule: string } | null;
  assietteLibelle: string | null;
  sequestre: 'AUCUN' | 'PRIX' | 'CAPITAL';
  finDelaiOpposition: string | null;
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
  minute: { numeroRepertoire: string; dateArchivage: string | null; archiveParNom: string | null; conservationJusquau: string } | null;
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


export interface TypeActeVue {
  code: TypeActe;
  libelle: string;
  codeReference: string;
  domaine: DomaineActe;
  domaineLibelle: string;
  complexe: boolean;
  assietteLibelle: string | null;
  enregistrement: string;
  publiciteFonciere: boolean;
  emolument: string;
  sequestre: 'AUCUN' | 'PRIX' | 'CAPITAL';
  formalites: string[];
  delaiOppositionJours: number | null;
  plusValue: boolean;
}

export interface Organisme {
  code: OrganismeFormalites;
  libelle: string;
  objet: string;
}

/* ------------------------------------------------------------------ *
 *  Session, identité de l'étude, clients, répertoire
 * ------------------------------------------------------------------ */

export interface Session {
  jeton: string;
  expiration: string;
  utilisateur: Utilisateur;
  doitChangerMotDePasse: boolean;
}

export interface CompteDemo {
  email: string;
  motDePasse: string;
  nom: string;
  role: Role;
  roleLibelle: string;
}

export interface IdentiteEtude {
  nom: string;
  sousTitre: string | null;
  monogramme: string | null;
  couleurPrincipale: string;
  couleurAccent: string;
  police: string;
  logo: string | null;
  adresse: string | null;
  telephone: string | null;
  email: string | null;
  ninea: string | null;
  mentionPiedDePage: string | null;
  miseAJourLe?: string | null;
  miseAJourPar?: string | null;
}

export type TypePersonne = 'PHYSIQUE' | 'MORALE';

export interface ClientResume {
  id: string;
  type: TypePersonne;
  nom: string;
  identifiant: string | null;
  telephone: string | null;
  email: string | null;
  kycValide: boolean;
  nbDossiers: number;
  nbEnCours: number;
  dernierDossier: string | null;
}

export interface Client {
  id: string;
  type: TypePersonne;
  nom: string;
  identifiant: string | null;
  telephone: string | null;
  email: string | null;
  adresse: string | null;
  regimeMatrimonial: string | null;
  notes: string | null;
  kycValide: boolean;
  dateCreation: string;
}

export interface DossierDuClient {
  dossierId: string;
  reference: string;
  intitule: string;
  typeActe: string;
  domaine: string;
  statut: StatutDossier;
  statutLibelle: string;
  qualite: string;
  qualiteLibelle: string;
  dateOuverture: string;
  dateSignature: string | null;
  numeroRepertoire: string | null;
  dateArchivage: string | null;
  notaire: string | null;
  clerc: string | null;
  solde: number;
}

export interface FicheClient {
  client: Client;
  nbDossiers: number;
  nbEnCours: number;
  nbActesSignes: number;
  fondsDetenus: number;
  dossiers: DossierDuClient[];
  personnesLiees: { clientId: string; nom: string; type: TypePersonne; dossiersCommuns: number }[];
}

export interface SaisieClient {
  type: TypePersonne;
  nom: string;
  identifiant?: string | null;
  telephone?: string | null;
  email?: string | null;
  adresse?: string | null;
  regimeMatrimonial?: string | null;
  notes?: string | null;
}

export type EtatArchivage = 'FORMALITES' | 'A_ARCHIVER' | 'ARCHIVEE';

export interface LigneRepertoire {
  numero: string;
  date: string;
  dossierId: string;
  reference: string;
  intitule: string;
  nature: string;
  forme: string;
  parties: { clientId: string | null; nom: string; qualite: string }[];
  enregistrement: { dateDepot: string | null; reference: string | null; dateAchevement: string | null } | null;
  droitsEnregistrement: number | null;
  notaire: string | null;
  etat: EtatArchivage;
  blocages: string[];
  emplacement: string | null;
  dateArchivage: string | null;
  conservationJusquau: string | null;
  nbPreuves: number;
}

export type TypePreuve = 'MINUTE_SIGNEE' | 'QUITTANCE_ENREGISTREMENT' | 'ATTESTATION_FORMALITE' | 'PIECE_ANNEXEE' | 'AUTRE';

export interface Preuve {
  id: number;
  dossierId: string;
  type: TypePreuve;
  typeLibelle: string;
  nomFichier: string;
  typeContenu: string;
  taille: number;
  empreinteSha256: string;
  commentaire: string | null;
  deposeLe: string;
  deposeParNom: string;
}

export interface Repertoire {
  annee: number;
  annees: number[];
  lignes: LigneRepertoire[];
  trimestres: { annee: number; trimestre: number; nbActes: number; premier: string; dernier: string; visaAvant: string }[];
}

export interface ResultatArchivage {
  dossierId: string;
  archive: boolean;
  numeroRepertoire: string | null;
  blocages: Blocage[];
}

/* ------------------------------------------------------------------ *
 *  Administration de la plateforme (super administrateur)
 * ------------------------------------------------------------------ */

export interface Compte {
  id: string;
  prenom: string;
  nom: string;
  nomComplet: string;
  email: string;
  role: Role;
  roleLibelle: string;
  initiales: string;
  actif: boolean;
  verrouille: boolean;
  verrouilleJusqua: string | null;
  doitChangerMotDePasse: boolean;
  derniereConnexion: string | null;
  dateEntree: string | null;
  desactiveLe: string | null;
  referenceNomination: string | null;
  notaireResponsableId: string | null;
  notaireResponsableNom: string | null;
  dossiersActifs: number;
}

export interface CompteEtMotDePasse {
  compte: Compte;
  motDePasseProvisoire: string;
}

export interface SaisieCompte {
  prenom: string;
  nom: string;
  email: string;
  role: Role;
  dateEntree?: string | null;
  referenceNomination?: string | null;
  notaireResponsableId?: string | null;
}

export type TypeEvenementAdmin =
  | 'CONNEXION_REUSSIE' | 'CONNEXION_ECHOUEE' | 'COMPTE_VERROUILLE' | 'COMPTE_DEVERROUILLE' | 'COMPTE_CREE'
  | 'COMPTE_MODIFIE' | 'COMPTE_DESACTIVE' | 'COMPTE_REACTIVE' | 'MOT_DE_PASSE_REINITIALISE' | 'MOT_DE_PASSE_CHANGE'
  | 'SESSIONS_REVOQUEES' | 'IDENTITE_MODIFIEE' | 'PARAMETRES_MODIFIES';

export interface EvenementAdmin {
  id: number;
  horodatage: string;
  type: TypeEvenementAdmin;
  typeLibelle: string;
  sensible: boolean;
  acteurId: string | null;
  acteurNom: string | null;
  cibleId: string | null;
  cibleNom: string | null;
  detail: string | null;
  adresseIp: string | null;
}

export interface ParametresSecurite {
  longueurMinimale: number;
  dureeSessionHeures: number;
  tentativesAvantVerrouillage: number;
  dureeVerrouillageMinutes: number;
  changementObligatoire: boolean;
  miseAJourLe?: string | null;
  miseAJourPar?: string | null;
}

export interface TableauAdmin {
  comptesActifs: number;
  comptesDesactives: number;
  comptesVerrouilles: number;
  motsDePasseProvisoires: number;
  jamaisConnectes: number;
  connexions24h: number;
  echecs24h: number;
  parRole: { role: Role; libelle: string; actifs: number }[];
  derniersEvenements: EvenementAdmin[];
  aSurveiller: Compte[];
  politique: ParametresSecurite;
  systeme: { version: string; base: string; donneesDemo: boolean; demarreLe: string; dossiers: number; dureeFonctionnement: string };
}
