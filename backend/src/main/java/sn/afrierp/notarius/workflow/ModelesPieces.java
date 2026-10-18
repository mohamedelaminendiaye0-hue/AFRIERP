package sn.afrierp.notarius.workflow;

import java.util.List;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.domain.CategoriePiece;
import sn.afrierp.notarius.domain.PieceJustificative;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.TypeActe;

/** Checklists standard par type d'acte (checklist intelligente du module GED). */
public final class ModelesPieces {

    private ModelesPieces() {
    }

    public record Modele(String libelle, CategoriePiece categorie, boolean obligatoire, String responsable,
                         StatutDossier exigibleA, Integer validiteJours) {

        Modele(String libelle, CategoriePiece categorie, boolean obligatoire, String responsable) {
            this(libelle, categorie, obligatoire, responsable, StatutDossier.DRAFTING, null);
        }

        public PieceJustificative instancier() {
            PieceJustificative p = new PieceJustificative();
            p.setLibelle(libelle);
            p.setCategorie(categorie);
            p.setObligatoire(obligatoire);
            p.setResponsable(responsable);
            p.setExigibleA(exigibleA);
            p.setValiditeJours(validiteJours);
            return p;
        }
    }

    public static final List<Modele> VENTE = List.of(
            new Modele("Titre foncier — copie certifiee conforme", CategoriePiece.FONCIER, true, "Vendeur"),
            new Modele("Etat des droits reels (Conservation fonciere)", CategoriePiece.FONCIER, true, "Etude",
                    StatutDossier.DRAFTING, Bareme.VALIDITE_ETAT_DROITS_REELS_JOURS),
            new Modele("Extrait d'urbanisme / note de renseignement", CategoriePiece.URBANISME, true, "Etude"),
            new Modele("Certificat de conformite du batiment", CategoriePiece.URBANISME, false, "Vendeur"),
            new Modele("Quitus fiscal du vendeur", CategoriePiece.FISCAL, true, "Vendeur"),
            new Modele("Quittances de taxe fonciere (3 dernieres annees)", CategoriePiece.FISCAL, true, "Vendeur"),
            new Modele("Etat civil et CNI — vendeur", CategoriePiece.ETAT_CIVIL, true, "Vendeur"),
            new Modele("Etat civil et CNI — acquereur", CategoriePiece.ETAT_CIVIL, true, "Acquereur"),
            new Modele("Certificat de mariage / regime matrimonial", CategoriePiece.ETAT_CIVIL, true, "Vendeur"),
            new Modele("Attestation de financement bancaire", CategoriePiece.BANCAIRE, false, "Acquereur"),
            new Modele("Mainlevee d'hypotheque anterieure", CategoriePiece.BANCAIRE, true, "Banque",
                    StatutDossier.READY_TO_SIGN, null));

    private static Modele oblig(String libelle, CategoriePiece c, String responsable) {
        return new Modele(libelle, c, true, responsable);
    }

    private static Modele facultative(String libelle, CategoriePiece c, String responsable) {
        return new Modele(libelle, c, false, responsable);
    }

    private static Modele aLaSignature(String libelle, CategoriePiece c, String responsable) {
        return new Modele(libelle, c, true, responsable, StatutDossier.READY_TO_SIGN, null);
    }

    private static Modele perissable(String libelle, CategoriePiece c, String responsable) {
        return new Modele(libelle, c, true, responsable, StatutDossier.DRAFTING, Bareme.VALIDITE_ETAT_DROITS_REELS_JOURS);
    }

    private static final CategoriePiece F = CategoriePiece.FONCIER;
    private static final CategoriePiece U = CategoriePiece.URBANISME;
    private static final CategoriePiece FI = CategoriePiece.FISCAL;
    private static final CategoriePiece EC = CategoriePiece.ETAT_CIVIL;
    private static final CategoriePiece B = CategoriePiece.BANCAIRE;
    private static final CategoriePiece S = CategoriePiece.SOCIETE;
    private static final CategoriePiece J = CategoriePiece.JUDICIAIRE;

    public static final List<Modele> PROMESSE = List.of(
            oblig("Titre foncier — copie certifiee conforme", F, "Vendeur"),
            perissable("Etat des droits reels (Conservation fonciere)", F, "Etude"),
            oblig("Etat civil et CNI — promettant", EC, "Vendeur"),
            oblig("Etat civil et CNI — beneficiaire", EC, "Acquereur"),
            facultative("Justificatif du depot de garantie", B, "Acquereur"));

    public static final List<Modele> HYPOTHEQUE = List.of(
            oblig("Convention de credit / offre de pret", B, "Banque"),
            oblig("Titre foncier affecte — copie certifiee conforme", F, "Emprunteur"),
            perissable("Etat des droits reels (Conservation fonciere)", F, "Etude"),
            oblig("Etat civil et CNI — constituant", EC, "Emprunteur"),
            facultative("Statuts et RCCM (constituant personne morale)", S, "Emprunteur"),
            aLaSignature("Pouvoir du representant de la banque", B, "Banque"));

    public static final List<Modele> MAINLEVEE = List.of(
            oblig("Attestation de remboursement integral du credit", B, "Banque"),
            oblig("Bordereau d'inscription hypothecaire", F, "Etude"),
            oblig("Titre foncier — copie certifiee conforme", F, "Proprietaire"),
            aLaSignature("Pouvoir du signataire de la banque", B, "Banque"));

    public static final List<Modele> BAIL_LONG = List.of(
            oblig("Titre foncier ou acte d'affectation du terrain (domaine de l'Etat)", F, "Bailleur"),
            perissable("Etat des droits reels (Conservation fonciere)", F, "Etude"),
            oblig("Plan de situation et de bornage", U, "Bailleur"),
            oblig("Etat civil / statuts du preneur", EC, "Preneur"),
            facultative("Programme de construction (bail a construction)", U, "Preneur"),
            facultative("Autorisation administrative (domaine prive de l'Etat)", J, "Bailleur"));

    public static final List<Modele> COPROPRIETE = List.of(
            oblig("Titre foncier de l'immeuble", F, "Promoteur"),
            oblig("Permis de construire", U, "Promoteur"),
            oblig("Plans de l'immeuble par niveau", U, "Architecte"),
            oblig("Etat descriptif de division (geometre)", F, "Geometre"),
            oblig("Tableau des tantiemes / quotes-parts des parties communes", F, "Geometre"),
            aLaSignature("Certificat de conformite", U, "Promoteur"));

    public static final List<Modele> BAIL_COMMERCIAL = List.of(
            oblig("Titre de propriete ou mandat du bailleur", F, "Bailleur"),
            oblig("Etat civil / statuts du bailleur", EC, "Bailleur"),
            oblig("RCCM et NINEA du preneur", S, "Preneur"),
            oblig("Descriptif et plan des locaux", U, "Bailleur"),
            facultative("Etat des lieux d'entree", U, "Parties"));

    public static final List<Modele> CONSTITUTION = List.of(
            oblig("Projet de statuts", S, "Etude"),
            oblig("Pieces d'identite des associes et dirigeants", EC, "Client"),
            oblig("Justificatif du siege social (bail ou attestation)", S, "Client"),
            oblig("Casier judiciaire du dirigeant", J, "Client"),
            oblig("Liste des souscripteurs (declaration de souscription et de versement)", S, "Etude"),
            aLaSignature("Attestation de depot du capital sur le sequestre", B, "Banque"));

    public static final List<Modele> CESSION_PARTS = List.of(
            oblig("Statuts a jour de la societe", S, "Client"),
            oblig("Extrait RCCM de moins de 3 mois", S, "Client"),
            oblig("Proces-verbal d'agrement du cessionnaire", S, "Client"),
            oblig("Pieces d'identite du cedant et du cessionnaire", EC, "Client"),
            oblig("Quitus fiscal de la societe", FI, "Client"));

    public static final List<Modele> MODIFICATION = List.of(
            oblig("Statuts a jour de la societe", S, "Client"),
            oblig("Extrait RCCM de moins de 3 mois", S, "Client"),
            oblig("Proces-verbal d'assemblee generale extraordinaire", S, "Client"),
            facultative("Rapport du commissaire aux apports / aux comptes", S, "Commissaire"),
            facultative("Attestation de depot des fonds (augmentation de capital)", B, "Banque"));

    public static final List<Modele> FUSION = List.of(
            oblig("Statuts et RCCM des societes concernees", S, "Client"),
            oblig("Traite de fusion ou proces-verbal de dissolution", S, "Client"),
            oblig("Rapport du commissaire a la fusion / du liquidateur", S, "Commissaire"),
            oblig("Etats financiers ou bilan de liquidation", FI, "Client"),
            oblig("Quitus fiscal", FI, "Client"));

    public static final List<Modele> FONDS_COMMERCE = List.of(
            oblig("Extrait RCCM du vendeur (inscription du fonds)", S, "Vendeur"),
            new Modele("Etat des privileges et nantissements (Greffe)", S, true, "Etude", StatutDossier.DRAFTING,
                    Bareme.VALIDITE_ETAT_DROITS_REELS_JOURS),
            oblig("Bail commercial des locaux", F, "Vendeur"),
            oblig("Agrement du bailleur a la cession du droit au bail", F, "Bailleur"),
            oblig("Chiffres d'affaires et resultats des 3 derniers exercices", FI, "Vendeur"),
            oblig("Pieces d'identite / statuts des parties", EC, "Parties"));

    public static final List<Modele> NANTISSEMENT = List.of(
            oblig("Convention de credit", B, "Banque"),
            oblig("Extrait RCCM du constituant", S, "Client"),
            oblig("Inventaire du materiel / description du fonds nanti", S, "Client"),
            oblig("Etat des inscriptions au RCCM", S, "Etude"));

    public static final List<Modele> SUCCESSION = List.of(
            oblig("Acte de deces", EC, "Heritiers"),
            oblig("Jugement ou certificat d'heredite (Tribunal)", J, "Heritiers"),
            oblig("Pieces d'identite des heritiers", EC, "Heritiers"),
            oblig("Inventaire des biens et titres fonciers", F, "Etude"),
            oblig("Releves des comptes bancaires du defunt", B, "Banques"));

    public static final List<Modele> DONATION = List.of(
            oblig("Pieces d'identite du donateur et des donataires", EC, "Famille"),
            oblig("Titres de propriete des biens donnes", F, "Donateur"),
            oblig("Livret de famille / acte de mariage", EC, "Famille"),
            facultative("Etat des droits reels (biens immobiliers)", F, "Etude"));

    public static final List<Modele> TESTAMENT = List.of(
            oblig("Piece d'identite du testateur", EC, "Testateur"),
            facultative("Testament olographe original (en cas de depot)", EC, "Testateur"),
            facultative("Pieces d'identite des temoins (testament authentique)", EC, "Temoins"));

    public static final List<Modele> CONTRAT_MARIAGE = List.of(
            oblig("Pieces d'identite des futurs epoux", EC, "Futurs epoux"),
            oblig("Extraits d'acte de naissance", EC, "Futurs epoux"),
            facultative("Inventaire des apports de chaque epoux", F, "Futurs epoux"));

    public static final List<Modele> REGIME_MATRIMONIAL = List.of(
            oblig("Acte de mariage", EC, "Epoux"),
            oblig("Pieces d'identite des epoux", EC, "Epoux"),
            oblig("Inventaire des biens communs et propres", F, "Epoux"),
            facultative("Contrat de mariage initial", EC, "Epoux"),
            facultative("Information des enfants majeurs et des creanciers", J, "Etude"));

    public static final List<Modele> PARTAGE_COMMUNAUTE = List.of(
            oblig("Jugement de divorce definitif", J, "Ex-epoux"),
            oblig("Acte de mariage", EC, "Ex-epoux"),
            oblig("Pieces d'identite des ex-epoux", EC, "Ex-epoux"),
            oblig("Inventaire et titres des biens communs", F, "Ex-epoux"),
            facultative("Etat des droits reels (biens immobiliers)", F, "Etude"));

    public static final List<Modele> PROCURATION = List.of(
            oblig("Piece d'identite du mandant", EC, "Mandant"),
            oblig("Piece d'identite du mandataire", EC, "Mandataire"),
            facultative("Justificatif de l'objet du mandat (titre du bien, releve de compte)", F, "Mandant"));

    public static final List<Modele> DEPOT = List.of(
            oblig("Acte sous seing prive original", J, "Deposants"),
            oblig("Pieces d'identite des deposants", EC, "Deposants"),
            facultative("Pouvoir du representant", J, "Deposants"));

    public static final List<Modele> NOTORIETE = List.of(
            oblig("Piece d'identite du requerant", EC, "Requerant"),
            oblig("Attestations et pieces d'identite de deux temoins", EC, "Temoins"),
            facultative("Pieces justificatives des faits invoques", J, "Requerant"));

    public static final List<Modele> ENCHERES = List.of(
            oblig("Cahier des charges de la vente", J, "Etude"),
            oblig("Titre de propriete / titre foncier", F, "Vendeur"),
            perissable("Etat des droits reels (Conservation fonciere)", F, "Etude"),
            facultative("Decision judiciaire ordonnant la vente (vente judiciaire)", J, "Tribunal"),
            aLaSignature("Avis de publicite de la vente publie", J, "Etude"));

    public static List<Modele> pour(TypeActe type) {
        return switch (type) {
            case VENTE_IMMOBILIERE -> VENTE;
            case PROMESSE_VENTE -> PROMESSE;
            case AFFECTATION_HYPOTHECAIRE -> HYPOTHEQUE;
            case MAINLEVEE_HYPOTHEQUE -> MAINLEVEE;
            case BAIL_EMPHYTEOTIQUE -> BAIL_LONG;
            case REGLEMENT_COPROPRIETE -> COPROPRIETE;
            case BAIL_COMMERCIAL -> BAIL_COMMERCIAL;
            case CONSTITUTION_SOCIETE -> CONSTITUTION;
            case CESSION_PARTS -> CESSION_PARTS;
            case MODIFICATION_STATUTS -> MODIFICATION;
            case FUSION_DISSOLUTION -> FUSION;
            case CESSION_FONDS_COMMERCE -> FONDS_COMMERCE;
            case NANTISSEMENT -> NANTISSEMENT;
            case SUCCESSION -> SUCCESSION;
            case DONATION -> DONATION;
            case TESTAMENT -> TESTAMENT;
            case CONTRAT_MARIAGE -> CONTRAT_MARIAGE;
            case CHANGEMENT_REGIME_MATRIMONIAL -> REGIME_MATRIMONIAL;
            case PARTAGE_COMMUNAUTE -> PARTAGE_COMMUNAUTE;
            case PROCURATION -> PROCURATION;
            case DEPOT_RANG_MINUTES -> DEPOT;
            case ACTE_NOTORIETE -> NOTORIETE;
            case VENTE_ENCHERES -> ENCHERES;
        };
    }
}
