package sn.afrierp.notarius.bareme;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sn.afrierp.notarius.domain.OrganismeFormalites;
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

    public record Debours(String code, String libelle, long montant, boolean foncier) {
    }

    /** Fonds a consigner sur le sequestre avant la signature, en plus des frais. */
    public enum Sequestre {
        AUCUN, PRIX, CAPITAL
    }

    /**
     * Parametres fiscaux et proceduraux d'un type d'acte.
     *
     * @param tauxEnregistrement droit proportionnel sur l'assiette ; null = droit fixe
     * @param droitFixe          droit fixe d'enregistrement (actes sans valeur ou non mutatifs)
     * @param publiciteFonciere  l'acte est publie au livre foncier (taxe de conservation fonciere)
     * @param emolumentFixe      emolument forfaitaire ; null = bareme proportionnel degressif
     * @param formalites         formalites a achever avant la cloture
     * @param delaiOppositionJours delai d'opposition des creanciers apres publication, pendant lequel
     *                           le prix reste sequestre (cession de fonds de commerce)
     */
    public record ParametresActe(Double tauxEnregistrement, long droitFixe, boolean publiciteFonciere,
                                 Long emolumentFixe, long emolumentFormalites, Sequestre sequestre,
                                 List<OrganismeFormalites> formalites, Integer delaiOppositionJours,
                                 boolean plusValue) {
    }

    private static ParametresActe proportionnel(double taux, boolean foncier, long formalites, Sequestre sequestre,
                                                OrganismeFormalites... organismes) {
        return new ParametresActe(taux, 0, foncier, null, formalites, sequestre, List.of(organismes), null, false);
    }

    private static ParametresActe forfaitaire(long droitFixe, boolean foncier, long emolument, long formalites,
                                              OrganismeFormalites... organismes) {
        return new ParametresActe(null, droitFixe, foncier, emolument, formalites, Sequestre.AUCUN, List.of(organismes), null, false);
    }

    private static final OrganismeFormalites DGID = OrganismeFormalites.DGID;
    private static final OrganismeFormalites CF = OrganismeFormalites.CONSERVATION_FONCIERE;
    private static final OrganismeFormalites RCCM = OrganismeFormalites.GREFFE_RCCM;
    private static final OrganismeFormalites NINEA = OrganismeFormalites.NINEA;
    private static final OrganismeFormalites AVIS = OrganismeFormalites.PUBLICATION_LEGALE;

    /** Catalogue : un jeu de parametres par type d'acte (valeurs de demonstration, a valider). */
    public static final Map<TypeActe, ParametresActe> CATALOGUE = new EnumMap<>(TypeActe.class);

    static {
        /* Immobilier, foncier & suretes */
        CATALOGUE.put(TypeActe.VENTE_IMMOBILIERE, new ParametresActe(0.05, 0, true, null, 150_000, Sequestre.PRIX, List.of(DGID, CF), null, true));
        CATALOGUE.put(TypeActe.PROMESSE_VENTE, forfaitaire(10_000, false, 150_000, 50_000, DGID));
        CATALOGUE.put(TypeActe.AFFECTATION_HYPOTHECAIRE, proportionnel(0.01, true, 120_000, Sequestre.AUCUN, CF));
        CATALOGUE.put(TypeActe.MAINLEVEE_HYPOTHEQUE, forfaitaire(25_000, true, 100_000, 50_000, CF));
        CATALOGUE.put(TypeActe.BAIL_EMPHYTEOTIQUE, proportionnel(0.02, true, 150_000, Sequestre.AUCUN, DGID, CF));
        CATALOGUE.put(TypeActe.REGLEMENT_COPROPRIETE, new ParametresActe(null, 50_000, true, null, 200_000, Sequestre.AUCUN, List.of(CF), null, false));
        CATALOGUE.put(TypeActe.BAIL_COMMERCIAL, proportionnel(0.02, false, 100_000, Sequestre.AUCUN, DGID));
        /* Societes & affaires (OHADA) */
        CATALOGUE.put(TypeActe.CONSTITUTION_SOCIETE, proportionnel(0.01, false, 200_000, Sequestre.CAPITAL, DGID, RCCM, NINEA, AVIS));
        CATALOGUE.put(TypeActe.CESSION_PARTS, proportionnel(0.01, false, 150_000, Sequestre.PRIX, DGID, RCCM));
        CATALOGUE.put(TypeActe.MODIFICATION_STATUTS, proportionnel(0.01, false, 150_000, Sequestre.AUCUN, DGID, RCCM, AVIS));
        CATALOGUE.put(TypeActe.FUSION_DISSOLUTION, proportionnel(0.01, false, 250_000, Sequestre.AUCUN, DGID, RCCM, AVIS));
        CATALOGUE.put(TypeActe.CESSION_FONDS_COMMERCE, new ParametresActe(0.05, 0, false, null, 150_000, Sequestre.PRIX, List.of(DGID, AVIS, RCCM), 30, false));
        CATALOGUE.put(TypeActe.NANTISSEMENT, proportionnel(0.01, false, 100_000, Sequestre.AUCUN, RCCM));
        /* Famille, successions & patrimoine */
        CATALOGUE.put(TypeActe.SUCCESSION, proportionnel(0.03, true, 175_000, Sequestre.AUCUN, DGID, CF));
        CATALOGUE.put(TypeActe.DONATION, proportionnel(0.03, true, 125_000, Sequestre.AUCUN, DGID, CF));
        CATALOGUE.put(TypeActe.TESTAMENT, forfaitaire(15_000, false, 75_000, 0));
        CATALOGUE.put(TypeActe.CONTRAT_MARIAGE, forfaitaire(15_000, false, 100_000, 25_000, DGID));
        // Homologation judiciaire « le cas echeant » : formalite complementaire ajoutee par le formaliste.
        CATALOGUE.put(TypeActe.CHANGEMENT_REGIME_MATRIMONIAL, forfaitaire(15_000, false, 150_000, 50_000, DGID));
        CATALOGUE.put(TypeActe.PARTAGE_COMMUNAUTE, proportionnel(0.01, true, 125_000, Sequestre.AUCUN, DGID, CF));
        CATALOGUE.put(TypeActe.PROCURATION, forfaitaire(5_000, false, 30_000, 0));
        /* Conservation, authentification & encheres */
        CATALOGUE.put(TypeActe.DEPOT_RANG_MINUTES, forfaitaire(5_000, false, 50_000, 0, DGID));
        CATALOGUE.put(TypeActe.ACTE_NOTORIETE, forfaitaire(5_000, false, 50_000, 0));
        CATALOGUE.put(TypeActe.VENTE_ENCHERES, proportionnel(0.05, true, 250_000, Sequestre.PRIX, DGID, CF));
    }

    public static ParametresActe parametres(TypeActe type) {
        return CATALOGUE.get(type);
    }


    public static final double CONSERVATION_FONCIERE_TAUX = 0.01;
    public static final long CONSERVATION_FONCIERE_MINIMUM = 25_000;

    /** Emoluments proportionnels du notaire : bareme degressif par tranches. */
    public static final List<Tranche> TRANCHES_EMOLUMENTS = List.of(
            new Tranche(0, 10_000_000L, 0.03),
            new Tranche(10_000_000, 30_000_000L, 0.02),
            new Tranche(30_000_000, 100_000_000L, 0.0125),
            new Tranche(100_000_000, null, 0.008));
    public static final long EMOLUMENTS_MINIMUM = 100_000;


    public static final List<Debours> DEBOURS = List.of(
            new Debours("DEB-ETAT-CIVIL", "Extraits d'état civil et copies certifiées", 25_000, false),
            new Debours("DEB-URBANISME", "Extrait d'urbanisme et note de renseignement", 45_000, true),
            new Debours("DEB-CADASTRE", "Relevé cadastral et bornage", 60_000, true),
            new Debours("DEB-TIMBRE", "Timbres fiscaux et papeterie légale", 35_000, false),
            new Debours("DEB-PUBLICITE", "Frais de publication / télépublication", 55_000, true),
            new Debours("DEB-TRANSPORT", "Déplacements et diligences administratives", 30_000, false));

    /** TVA applicable aux seuls emoluments et honoraires de l'etude. */
    public static final double TVA_TAUX = 0.18;

    /* --- Conformite CDC / maniement de fonds --- */
    public static final int CDC_SEUIL_IMMOBILISATION_JOURS = 90;
    public static final int CDC_SEUIL_PRE_ALERTE_JOURS = 75;
    public static final long CDC_SEUIL_MONTANT_MINIMUM = 50_000;
    public static final String CDC_REFERENCE =
            "Règle d'immobilisation des fonds clients — consignation CDC (paramétrable)";

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



    /** Duree legale minimale de conservation des minutes, en annees. */
    public static final int DUREE_CONSERVATION_MINUTES_ANS = 75;

    /** Taxe sur la plus-value immobiliere, retenue sur le prix revenant au vendeur. */
    public static final double TAUX_PLUS_VALUE = 0.15;

    /* --- Agenda de l'etude --- */
    public static final java.time.LocalTime OUVERTURE_ETUDE = java.time.LocalTime.of(8, 0);
    public static final java.time.LocalTime FERMETURE_ETUDE = java.time.LocalTime.of(18, 0);
    /** Jours d'ouverture : du lundi (1) au vendredi (5). */
    public static final Set<java.time.DayOfWeek> JOURS_OUVRES = java.util.EnumSet.range(
            java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY);

    /* --- Objectifs internes (pilotage, non reglementaires) --- */
    public static final int OBJECTIF_DELAI_TRAITEMENT_JOURS = 45;
    public static final double OBJECTIF_TAUX_RETARD_MAXIMUM = 0.15;
}
