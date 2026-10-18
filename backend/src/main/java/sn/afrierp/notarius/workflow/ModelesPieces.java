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
            new Modele("Titre foncier — copie certifiée conforme", CategoriePiece.FONCIER, true, "Vendeur"),
            new Modele("État des droits réels (Conservation foncière)", CategoriePiece.FONCIER, true, "Étude",
                    StatutDossier.DRAFTING, Bareme.VALIDITE_ETAT_DROITS_REELS_JOURS),
            new Modele("Extrait d'urbanisme / note de renseignement", CategoriePiece.URBANISME, true, "Étude"),
            new Modele("Certificat de conformité du bâtiment", CategoriePiece.URBANISME, false, "Vendeur"),
            new Modele("Quitus fiscal du vendeur", CategoriePiece.FISCAL, true, "Vendeur"),
            new Modele("Quittances de taxe foncière (3 dernières années)", CategoriePiece.FISCAL, true, "Vendeur"),
            new Modele("État civil et CNI — vendeur", CategoriePiece.ETAT_CIVIL, true, "Vendeur"),
            new Modele("État civil et CNI — acquéreur", CategoriePiece.ETAT_CIVIL, true, "Acquéreur"),
            new Modele("Certificat de mariage / régime matrimonial", CategoriePiece.ETAT_CIVIL, true, "Vendeur"),
            new Modele("Attestation de financement bancaire", CategoriePiece.BANCAIRE, false, "Acquéreur"),
            new Modele("Mainlevée d'hypothèque antérieure", CategoriePiece.BANCAIRE, true, "Banque",
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
            oblig("Titre foncier — copie certifiée conforme", F, "Vendeur"),
            perissable("État des droits réels (Conservation foncière)", F, "Étude"),
            oblig("État civil et CNI — promettant", EC, "Vendeur"),
            oblig("État civil et CNI — bénéficiaire", EC, "Acquéreur"),
            facultative("Justificatif du dépôt de garantie", B, "Acquéreur"));

    public static final List<Modele> HYPOTHEQUE = List.of(
            oblig("Convention de crédit / offre de prêt", B, "Banque"),
            oblig("Titre foncier affecté — copie certifiée conforme", F, "Emprunteur"),
            perissable("État des droits réels (Conservation foncière)", F, "Étude"),
            oblig("État civil et CNI — constituant", EC, "Emprunteur"),
            facultative("Statuts et RCCM (constituant personne morale)", S, "Emprunteur"),
            aLaSignature("Pouvoir du représentant de la banque", B, "Banque"));

    public static final List<Modele> MAINLEVEE = List.of(
            oblig("Attestation de remboursement intégral du crédit", B, "Banque"),
            oblig("Bordereau d'inscription hypothécaire", F, "Étude"),
            oblig("Titre foncier — copie certifiée conforme", F, "Propriétaire"),
            aLaSignature("Pouvoir du signataire de la banque", B, "Banque"));

    public static final List<Modele> BAIL_LONG = List.of(
            oblig("Titre foncier ou acte d'affectation du terrain (domaine de l'État)", F, "Bailleur"),
            perissable("État des droits réels (Conservation foncière)", F, "Étude"),
            oblig("Plan de situation et de bornage", U, "Bailleur"),
            oblig("État civil / statuts du preneur", EC, "Preneur"),
            facultative("Programme de construction (bail à construction)", U, "Preneur"),
            facultative("Autorisation administrative (domaine privé de l'État)", J, "Bailleur"));

    public static final List<Modele> COPROPRIETE = List.of(
            oblig("Titre foncier de l'immeuble", F, "Promoteur"),
            oblig("Permis de construire", U, "Promoteur"),
            oblig("Plans de l'immeuble par niveau", U, "Architecte"),
            oblig("État descriptif de division (géomètre)", F, "Géomètre"),
            oblig("Tableau des tantièmes / quotes-parts des parties communes", F, "Géomètre"),
            aLaSignature("Certificat de conformité", U, "Promoteur"));

    public static final List<Modele> BAIL_COMMERCIAL = List.of(
            oblig("Titre de propriété ou mandat du bailleur", F, "Bailleur"),
            oblig("État civil / statuts du bailleur", EC, "Bailleur"),
            oblig("RCCM et NINEA du preneur", S, "Preneur"),
            oblig("Descriptif et plan des locaux", U, "Bailleur"),
            facultative("État des lieux d'entrée", U, "Parties"));

    public static final List<Modele> CONSTITUTION = List.of(
            oblig("Projet de statuts", S, "Étude"),
            oblig("Pièces d'identité des associés et dirigeants", EC, "Client"),
            oblig("Justificatif du siège social (bail ou attestation)", S, "Client"),
            oblig("Casier judiciaire du dirigeant", J, "Client"),
            oblig("Liste des souscripteurs (déclaration de souscription et de versement)", S, "Étude"),
            aLaSignature("Attestation de dépôt du capital sur le séquestre", B, "Banque"));

    public static final List<Modele> CESSION_PARTS = List.of(
            oblig("Statuts à jour de la société", S, "Client"),
            oblig("Extrait RCCM de moins de 3 mois", S, "Client"),
            oblig("Procès-verbal d'agrément du cessionnaire", S, "Client"),
            oblig("Pièces d'identité du cédant et du cessionnaire", EC, "Client"),
            oblig("Quitus fiscal de la société", FI, "Client"));

    public static final List<Modele> MODIFICATION = List.of(
            oblig("Statuts à jour de la société", S, "Client"),
            oblig("Extrait RCCM de moins de 3 mois", S, "Client"),
            oblig("Procès-verbal d'assemblée générale extraordinaire", S, "Client"),
            facultative("Rapport du commissaire aux apports / aux comptes", S, "Commissaire"),
            facultative("Attestation de dépôt des fonds (augmentation de capital)", B, "Banque"));

    public static final List<Modele> FUSION = List.of(
            oblig("Statuts et RCCM des sociétés concernées", S, "Client"),
            oblig("Traite de fusion ou procès-verbal de dissolution", S, "Client"),
            oblig("Rapport du commissaire à la fusion / du liquidateur", S, "Commissaire"),
            oblig("États financiers ou bilan de liquidation", FI, "Client"),
            oblig("Quitus fiscal", FI, "Client"));

    public static final List<Modele> FONDS_COMMERCE = List.of(
            oblig("Extrait RCCM du vendeur (inscription du fonds)", S, "Vendeur"),
            new Modele("État des privilèges et nantissements (Greffe)", S, true, "Étude", StatutDossier.DRAFTING,
                    Bareme.VALIDITE_ETAT_DROITS_REELS_JOURS),
            oblig("Bail commercial des locaux", F, "Vendeur"),
            oblig("Agrément du bailleur à la cession du droit au bail", F, "Bailleur"),
            oblig("Chiffres d'affaires et résultats des 3 derniers exercices", FI, "Vendeur"),
            oblig("Pièces d'identité / statuts des parties", EC, "Parties"));

    public static final List<Modele> NANTISSEMENT = List.of(
            oblig("Convention de crédit", B, "Banque"),
            oblig("Extrait RCCM du constituant", S, "Client"),
            oblig("Inventaire du matériel / description du fonds nanti", S, "Client"),
            oblig("État des inscriptions au RCCM", S, "Étude"));

    public static final List<Modele> SUCCESSION = List.of(
            oblig("Acte de décès", EC, "Héritiers"),
            oblig("Jugement ou certificat d'hérédité (Tribunal)", J, "Héritiers"),
            oblig("Pièces d'identité des héritiers", EC, "Héritiers"),
            oblig("Inventaire des biens et titres fonciers", F, "Étude"),
            oblig("Relevés des comptes bancaires du défunt", B, "Banques"));

    public static final List<Modele> DONATION = List.of(
            oblig("Pièces d'identité du donateur et des donataires", EC, "Famille"),
            oblig("Titres de propriété des biens donnés", F, "Donateur"),
            oblig("Livret de famille / acte de mariage", EC, "Famille"),
            facultative("État des droits réels (biens immobiliers)", F, "Étude"));

    public static final List<Modele> TESTAMENT = List.of(
            oblig("Pièce d'identité du testateur", EC, "Testateur"),
            facultative("Testament olographe original (en cas de dépôt)", EC, "Testateur"),
            facultative("Pièces d'identité des témoins (testament authentique)", EC, "Témoins"));

    public static final List<Modele> CONTRAT_MARIAGE = List.of(
            oblig("Pièces d'identité des futurs époux", EC, "Futurs époux"),
            oblig("Extraits d'acte de naissance", EC, "Futurs époux"),
            facultative("Inventaire des apports de chaque époux", F, "Futurs époux"));

    public static final List<Modele> REGIME_MATRIMONIAL = List.of(
            oblig("Acte de mariage", EC, "Époux"),
            oblig("Pièces d'identité des époux", EC, "Époux"),
            oblig("Inventaire des biens communs et propres", F, "Époux"),
            facultative("Contrat de mariage initial", EC, "Époux"),
            facultative("Information des enfants majeurs et des créanciers", J, "Étude"));

    public static final List<Modele> PARTAGE_COMMUNAUTE = List.of(
            oblig("Jugement de divorce définitif", J, "Ex-époux"),
            oblig("Acte de mariage", EC, "Ex-époux"),
            oblig("Pièces d'identité des ex-époux", EC, "Ex-époux"),
            oblig("Inventaire et titres des biens communs", F, "Ex-époux"),
            facultative("État des droits réels (biens immobiliers)", F, "Étude"));

    public static final List<Modele> PROCURATION = List.of(
            oblig("Pièce d'identité du mandant", EC, "Mandant"),
            oblig("Pièce d'identité du mandataire", EC, "Mandataire"),
            facultative("Justificatif de l'objet du mandat (titre du bien, relevé de compte)", F, "Mandant"));

    public static final List<Modele> DEPOT = List.of(
            oblig("Acte sous seing privé original", J, "Déposants"),
            oblig("Pièces d'identité des déposants", EC, "Déposants"),
            facultative("Pouvoir du représentant", J, "Déposants"));

    public static final List<Modele> NOTORIETE = List.of(
            oblig("Pièce d'identité du requérant", EC, "Requérant"),
            oblig("Attestations et pièces d'identité de deux témoins", EC, "Témoins"),
            facultative("Pièces justificatives des faits invoqués", J, "Requérant"));

    public static final List<Modele> ENCHERES = List.of(
            oblig("Cahier des charges de la vente", J, "Étude"),
            oblig("Titre de propriété / titre foncier", F, "Vendeur"),
            perissable("État des droits réels (Conservation foncière)", F, "Étude"),
            facultative("Décision judiciaire ordonnant la vente (vente judiciaire)", J, "Tribunal"),
            aLaSignature("Avis de publicité de la vente publié", J, "Étude"));

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
