package sn.afrierp.notarius.domain;

public enum ModeSignature {
    PHYSIQUE("Signature physique en étude"),
    ELECTRONIQUE_GAINDE("Signature électronique — GAINDE 2000");

    private final String libelle;

    ModeSignature(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
