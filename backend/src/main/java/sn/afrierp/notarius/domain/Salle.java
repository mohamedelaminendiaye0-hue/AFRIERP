package sn.afrierp.notarius.domain;

public enum Salle {
    SALLE_SIGNATURES("Salle des signatures", true),
    BUREAU_NOTAIRE("Bureau du notaire", false),
    SALLE_REUNION("Salle de réunion", true),
    BUREAU_CLERC("Bureau du clerc", false),
    VISIOCONFERENCE("Visioconférence", false);

    private final String libelle;
    /** Salle partagee : deux rendez-vous ne peuvent pas s'y chevaucher. */
    private final boolean partagee;

    Salle(String libelle, boolean partagee) {
        this.libelle = libelle;
        this.partagee = partagee;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean estPartagee() {
        return partagee;
    }
}
