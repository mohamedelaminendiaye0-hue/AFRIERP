package sn.afrierp.notarius.domain;

/** Grands domaines d'intervention de l'etude notariale. */
public enum DomaineActe {
    IMMOBILIER("Immobilier, foncier & sûretés"),
    AFFAIRES("Sociétés & affaires (OHADA)"),
    FAMILLE("Famille, successions & patrimoine"),
    AUTHENTIFICATION("Conservation, authentification & enchères");

    private final String libelle;

    DomaineActe(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
