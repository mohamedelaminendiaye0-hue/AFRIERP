package sn.afrierp.notarius.domain;

/** Role d'une partie dans un acte. */
public enum QualiteIntervenant {
    VENDEUR("Vendeur"),
    ACQUEREUR("Acquéreur"),
    DONATEUR("Donateur"),
    DONATAIRE("Donataire"),
    HERITIER("Héritier"),
    BANQUE("Banque"),
    SOCIETE("Société"),
    MANDATAIRE("Mandataire"),
    MANDANT("Mandant"),
    EMPRUNTEUR("Emprunteur"),
    ASSOCIE("Associé"),
    EPOUX("Époux"),
    CEDANT("Cédant"),
    CESSIONNAIRE("Cessionnaire");

    private final String libelle;

    QualiteIntervenant(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
