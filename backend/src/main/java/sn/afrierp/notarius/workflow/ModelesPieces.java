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

    public static final List<Modele> SOCIETE = List.of(
            new Modele("Statuts de la societe", CategoriePiece.SOCIETE, true, "Client"),
            new Modele("Registre de commerce (RCCM)", CategoriePiece.SOCIETE, true, "Client"),
            new Modele("NINEA et quitus fiscal", CategoriePiece.FISCAL, true, "Client"),
            new Modele("Proces-verbal d'assemblee generale", CategoriePiece.SOCIETE, true, "Client"),
            new Modele("Pieces d'identite des associes", CategoriePiece.ETAT_CIVIL, true, "Client"),
            new Modele("Attestation de depot du capital", CategoriePiece.BANCAIRE, true, "Banque",
                    StatutDossier.READY_TO_SIGN, null));

    public static final List<Modele> FAMILLE = List.of(
            new Modele("Acte de deces / acte de donation", CategoriePiece.ETAT_CIVIL, true, "Famille"),
            new Modele("Certificat d'heredite / livret de famille", CategoriePiece.ETAT_CIVIL, true, "Famille"),
            new Modele("Pieces d'identite des parties", CategoriePiece.ETAT_CIVIL, true, "Famille"),
            new Modele("Inventaire des biens et titres fonciers", CategoriePiece.FONCIER, true, "Etude"),
            new Modele("Declaration fiscale (succession / donation)", CategoriePiece.FISCAL, true, "Etude"));

    public static List<Modele> pour(TypeActe type) {
        return switch (type) {
            case DROIT_DES_SOCIETES -> SOCIETE;
            case SUCCESSION, DONATION_FAMILLE -> FAMILLE;
            default -> VENTE;
        };
    }
}
