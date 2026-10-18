package sn.afrierp.notarius.domain;

public enum TypeActe {
    VENTE_IMMOBILIERE("Vente immobiliere", "VI"),
    DROIT_DES_SOCIETES("Droit des societes", "DS"),
    SUCCESSION("Succession", "SU"),
    DONATION_FAMILLE("Donation / Famille", "DF"),
    BAIL_COMMERCIAL("Bail commercial", "BC"),
    CREDIT_HYPOTHECAIRE("Credit hypothecaire", "CH");

    private final String libelle;
    private final String code;

    TypeActe(String libelle, String code) {
        this.libelle = libelle;
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCode() {
        return code;
    }
}
