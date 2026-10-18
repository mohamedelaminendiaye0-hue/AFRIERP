package sn.afrierp.notarius.domain;

public enum TypeActe {
    VENTE_IMMOBILIERE("Vente immobiliere", "VI", true),
    DROIT_DES_SOCIETES("Droit des societes", "DS", true),
    SUCCESSION("Succession", "SU", false),
    DONATION_FAMILLE("Donation / Famille", "DF", false),
    BAIL_COMMERCIAL("Bail commercial", "BC", false),
    CREDIT_HYPOTHECAIRE("Credit hypothecaire", "CH", false);

    private final String libelle;
    private final String code;
    private final boolean complexe;

    TypeActe(String libelle, String code, boolean complexe) {
        this.libelle = libelle;
        this.code = code;
        this.complexe = complexe;
    }

    /** Dossier complexe : supervise par le principal clerc avant soumission au notaire. */
    public boolean estComplexe() {
        return complexe;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCode() {
        return code;
    }
}
