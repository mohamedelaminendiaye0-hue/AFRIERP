package sn.afrierp.notarius.bareme;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.TypeActe;

/**
 * BAREME PARAMETRABLE — taux, tranches et seuils reglementaires.
 *
 * <p><b>Avertissement de conformite</b> : les valeurs ci-dessous sont plausibles
 * pour la zone UEMOA/OHADA et le Senegal mais constituent des parametres de
 * demonstration. Elles doivent etre confrontees aux textes en vigueur (Code
 * general des impots, tarif des notaires, textes CDC) et validees par le
 * service juridique de l'etude avant toute exploitation reelle.
 *
 * <p>Une seule source de verite, versionnee : un decompte conserve
 * {@code versionBareme}, ce qui le rend reproductible apres revalorisation.
 */
public final class Bareme {

    private Bareme() {
    }

    public static final String VERSION = "SN-2026.1";

    public record Tranche(long plancher, Long plafond, double taux) {
    }

    public record Debours(String code, String libelle, long montant) {
    }

    /** Droits d'enregistrement de la mutation, par type d'acte. */
    public static final Map<TypeActe, Double> DROITS_ENREGISTREMENT = new EnumMap<>(Map.of(
            TypeActe.VENTE_IMMOBILIERE, 0.05,
            TypeActe.DROIT_DES_SOCIETES, 0.01,
            TypeActe.SUCCESSION, 0.03,
            TypeActe.DONATION_FAMILLE, 0.03,
            TypeActe.BAIL_COMMERCIAL, 0.02,
            TypeActe.CREDIT_HYPOTHECAIRE, 0.01));

    public static final double CONSERVATION_FONCIERE_TAUX = 0.01;
    public static final long CONSERVATION_FONCIERE_MINIMUM = 25_000;

    /** Emoluments proportionnels du notaire : bareme degressif par tranches. */
    public static final List<Tranche> TRANCHES_EMOLUMENTS = List.of(
            new Tranche(0, 10_000_000L, 0.03),
            new Tranche(10_000_000, 30_000_000L, 0.02),
            new Tranche(30_000_000, 100_000_000L, 0.0125),
            new Tranche(100_000_000, null, 0.008));
    public static final long EMOLUMENTS_MINIMUM = 100_000;

    /** Emoluments de formalites (forfait par acte). */
    public static final Map<TypeActe, Long> EMOLUMENTS_FORMALITES = new EnumMap<>(Map.of(
            TypeActe.VENTE_IMMOBILIERE, 150_000L,
            TypeActe.DROIT_DES_SOCIETES, 200_000L,
            TypeActe.SUCCESSION, 175_000L,
            TypeActe.DONATION_FAMILLE, 125_000L,
            TypeActe.BAIL_COMMERCIAL, 100_000L,
            TypeActe.CREDIT_HYPOTHECAIRE, 120_000L));

    public static final List<Debours> DEBOURS = List.of(
            new Debours("DEB-ETAT-CIVIL", "Extraits d'etat civil et copies certifiees", 25_000),
            new Debours("DEB-URBANISME", "Extrait d'urbanisme et note de renseignement", 45_000),
            new Debours("DEB-CADASTRE", "Releve cadastral et bornage", 60_000),
            new Debours("DEB-TIMBRE", "Timbres fiscaux et papeterie legale", 35_000),
            new Debours("DEB-PUBLICITE", "Frais de publication / telepublication", 55_000),
            new Debours("DEB-TRANSPORT", "Deplacements et diligences administratives", 30_000));

    /** TVA applicable aux seuls emoluments et honoraires de l'etude. */
    public static final double TVA_TAUX = 0.18;

    /* --- Conformite CDC / maniement de fonds --- */
    public static final int CDC_SEUIL_IMMOBILISATION_JOURS = 90;
    public static final int CDC_SEUIL_PRE_ALERTE_JOURS = 75;
    public static final long CDC_SEUIL_MONTANT_MINIMUM = 50_000;
    public static final String CDC_REFERENCE =
            "Regle d'immobilisation des fonds clients — consignation CDC (parametrable)";

    /* --- Workflow des dossiers --- */

    /** Delai cible par statut, en jours ; au-dela, le clerc et le notaire sont notifies. */
    public static final Map<StatutDossier, Integer> DELAIS_CIBLES_JOURS = new EnumMap<>(Map.of(
            StatutDossier.DRAFT, 3,
            StatutDossier.PENDING_DOCS, 30,
            StatutDossier.DRAFTING, 15,
            StatutDossier.REVIEW, 5,
            StatutDossier.TAXING, 3,
            StatutDossier.FUNDING, 15,
            StatutDossier.READY_TO_SIGN, 10,
            StatutDossier.SIGNED, 5,
            StatutDossier.POST_ACT, 30));

    /** Validite de l'etat des droits reels delivre par la Conservation fonciere (3 mois). */
    public static final int VALIDITE_ETAT_DROITS_REELS_JOURS = 90;

    /** Types d'acte dont le prix doit etre sequestre avant signature, en plus des frais. */
    public static final Set<TypeActe> SEQUESTRE_PRIX_EXIGE = Set.of(TypeActe.VENTE_IMMOBILIERE);

    /* --- Objectifs internes (pilotage, non reglementaires) --- */
    public static final int OBJECTIF_DELAI_TRAITEMENT_JOURS = 45;
    public static final double OBJECTIF_TAUX_RETARD_MAXIMUM = 0.15;
}
