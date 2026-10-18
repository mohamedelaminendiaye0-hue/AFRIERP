package sn.afrierp.notarius.domain;

/**
 * Catalogue des actes et demarches traites par l'etude. L'identite de l'acte est
 * ici ; ses parametres fiscaux et proceduraux (taux, sequestre, formalites) sont
 * dans {@code Bareme.CATALOGUE}, et sa checklist dans {@code ModelesPieces}.
 */
public enum TypeActe {
    /* --- Immobilier, foncier & suretes --- */
    VENTE_IMMOBILIERE("Vente immobilière", "VI", DomaineActe.IMMOBILIER, true, "Prix de vente"),
    PROMESSE_VENTE("Promesse / compromis de vente", "PV", DomaineActe.IMMOBILIER, false, "Prix convenu"),
    AFFECTATION_HYPOTHECAIRE("Affectation hypothécaire (prêt bancaire)", "AH", DomaineActe.IMMOBILIER, true, "Montant du crédit garanti"),
    MAINLEVEE_HYPOTHEQUE("Mainlevée d'hypothèque", "MH", DomaineActe.IMMOBILIER, false, "Montant de l'inscription levée"),
    BAIL_EMPHYTEOTIQUE("Bail emphytéotique / bail à construction", "BE", DomaineActe.IMMOBILIER, true, "Loyers cumulés sur la durée"),
    REGLEMENT_COPROPRIETE("Règlement de copropriété & état descriptif de division", "RC", DomaineActe.IMMOBILIER, true, "Valeur de l'immeuble"),
    BAIL_COMMERCIAL("Bail commercial", "BC", DomaineActe.IMMOBILIER, false, "Loyers cumulés sur la durée"),

    /* --- Societes & affaires (OHADA) --- */
    CONSTITUTION_SOCIETE("Constitution de société (SARL, SA, SAS)", "CS", DomaineActe.AFFAIRES, true, "Capital social"),
    CESSION_PARTS("Cession de parts sociales / d'actions", "CP", DomaineActe.AFFAIRES, true, "Prix de cession"),
    MODIFICATION_STATUTS("Modification statutaire (capital, forme, siège)", "MS", DomaineActe.AFFAIRES, false, "Variation de capital"),
    FUSION_DISSOLUTION("Fusion, dissolution & liquidation de société", "FD", DomaineActe.AFFAIRES, true, "Actif net apporté ou partagé"),
    CESSION_FONDS_COMMERCE("Cession de fonds de commerce", "FC", DomaineActe.AFFAIRES, true, "Prix du fonds"),
    NANTISSEMENT("Nantissement de fonds de commerce ou de matériel", "NA", DomaineActe.AFFAIRES, false, "Montant de la créance garantie"),

    /* --- Famille, successions & patrimoine --- */
    SUCCESSION("Succession (notoriété, déclaration, partage)", "SU", DomaineActe.FAMILLE, true, "Actif net successoral"),
    DONATION("Donation / donation-partage", "DO", DomaineActe.FAMILLE, false, "Valeur des biens donnés"),
    TESTAMENT("Testament (authentique ou dépôt d'olographe)", "TE", DomaineActe.FAMILLE, false, null),
    CONTRAT_MARIAGE("Contrat de mariage", "CM", DomaineActe.FAMILLE, false, null),
    CHANGEMENT_REGIME_MATRIMONIAL("Changement de régime matrimonial", "RM", DomaineActe.FAMILLE, false, null),
    PARTAGE_COMMUNAUTE("Partage & liquidation de communauté (divorce)", "PC", DomaineActe.FAMILLE, false, "Actif net partagé"),
    PROCURATION("Procuration notariée / mandat de protection future", "PR", DomaineActe.FAMILLE, false, null),

    /* --- Conservation, authentification & encheres --- */
    DEPOT_RANG_MINUTES("Dépôt d'acte sous seing privé au rang des minutes", "DM", DomaineActe.AUTHENTIFICATION, false, null),
    ACTE_NOTORIETE("Acte de notoriété / constat notarié", "AN", DomaineActe.AUTHENTIFICATION, false, null),
    VENTE_ENCHERES("Vente aux enchères / adjudication", "VE", DomaineActe.AUTHENTIFICATION, true, "Prix d'adjudication");

    private final String libelle;
    private final String code;
    private final DomaineActe domaine;
    private final boolean complexe;
    /** Libelle de l'assiette de taxation ; null pour un acte sans assiette (droit et emolument fixes). */
    private final String assietteLibelle;

    TypeActe(String libelle, String code, DomaineActe domaine, boolean complexe, String assietteLibelle) {
        this.libelle = libelle;
        this.code = code;
        this.domaine = domaine;
        this.complexe = complexe;
        this.assietteLibelle = assietteLibelle;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCode() {
        return code;
    }

    public DomaineActe getDomaine() {
        return domaine;
    }

    /** Dossier complexe : supervise par le principal clerc avant soumission au notaire. */
    public boolean estComplexe() {
        return complexe;
    }

    public String getAssietteLibelle() {
        return assietteLibelle;
    }

    /** L'acte se taxe-t-il sur une valeur (prix, capital, credit) ? */
    public boolean aUneAssiette() {
        return assietteLibelle != null;
    }
}
