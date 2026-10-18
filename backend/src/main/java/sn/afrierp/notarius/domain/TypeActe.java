package sn.afrierp.notarius.domain;

/**
 * Catalogue des actes et demarches traites par l'etude. L'identite de l'acte est
 * ici ; ses parametres fiscaux et proceduraux (taux, sequestre, formalites) sont
 * dans {@code Bareme.CATALOGUE}, et sa checklist dans {@code ModelesPieces}.
 */
public enum TypeActe {
    /* --- Immobilier, foncier & suretes --- */
    VENTE_IMMOBILIERE("Vente immobiliere", "VI", DomaineActe.IMMOBILIER, true, "Prix de vente"),
    PROMESSE_VENTE("Promesse / compromis de vente", "PV", DomaineActe.IMMOBILIER, false, "Prix convenu"),
    AFFECTATION_HYPOTHECAIRE("Affectation hypothecaire (pret bancaire)", "AH", DomaineActe.IMMOBILIER, true, "Montant du credit garanti"),
    MAINLEVEE_HYPOTHEQUE("Mainlevee d'hypotheque", "MH", DomaineActe.IMMOBILIER, false, "Montant de l'inscription levee"),
    BAIL_EMPHYTEOTIQUE("Bail emphyteotique / bail a construction", "BE", DomaineActe.IMMOBILIER, true, "Loyers cumules sur la duree"),
    REGLEMENT_COPROPRIETE("Reglement de copropriete & etat descriptif de division", "RC", DomaineActe.IMMOBILIER, true, "Valeur de l'immeuble"),
    BAIL_COMMERCIAL("Bail commercial", "BC", DomaineActe.IMMOBILIER, false, "Loyers cumules sur la duree"),

    /* --- Societes & affaires (OHADA) --- */
    CONSTITUTION_SOCIETE("Constitution de societe (SARL, SA, SAS)", "CS", DomaineActe.AFFAIRES, true, "Capital social"),
    CESSION_PARTS("Cession de parts sociales / d'actions", "CP", DomaineActe.AFFAIRES, true, "Prix de cession"),
    MODIFICATION_STATUTS("Modification statutaire (capital, forme, siege)", "MS", DomaineActe.AFFAIRES, false, "Variation de capital"),
    FUSION_DISSOLUTION("Fusion, dissolution & liquidation de societe", "FD", DomaineActe.AFFAIRES, true, "Actif net apporte ou partage"),
    CESSION_FONDS_COMMERCE("Cession de fonds de commerce", "FC", DomaineActe.AFFAIRES, true, "Prix du fonds"),
    NANTISSEMENT("Nantissement de fonds de commerce ou de materiel", "NA", DomaineActe.AFFAIRES, false, "Montant de la creance garantie"),

    /* --- Famille, successions & patrimoine --- */
    SUCCESSION("Succession (notoriete, declaration, partage)", "SU", DomaineActe.FAMILLE, true, "Actif net successoral"),
    DONATION("Donation / donation-partage", "DO", DomaineActe.FAMILLE, false, "Valeur des biens donnes"),
    TESTAMENT("Testament (authentique ou depot d'olographe)", "TE", DomaineActe.FAMILLE, false, null),
    CONTRAT_MARIAGE("Contrat de mariage", "CM", DomaineActe.FAMILLE, false, null),
    CHANGEMENT_REGIME_MATRIMONIAL("Changement de regime matrimonial", "RM", DomaineActe.FAMILLE, false, null),
    PARTAGE_COMMUNAUTE("Partage & liquidation de communaute (divorce)", "PC", DomaineActe.FAMILLE, false, "Actif net partage"),
    PROCURATION("Procuration notariee / mandat de protection future", "PR", DomaineActe.FAMILLE, false, null),

    /* --- Conservation, authentification & encheres --- */
    DEPOT_RANG_MINUTES("Depot d'acte sous seing prive au rang des minutes", "DM", DomaineActe.AUTHENTIFICATION, false, null),
    ACTE_NOTORIETE("Acte de notoriete / constat notarie", "AN", DomaineActe.AUTHENTIFICATION, false, null),
    VENTE_ENCHERES("Vente aux encheres / adjudication", "VE", DomaineActe.AUTHENTIFICATION, true, "Prix d'adjudication");

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
