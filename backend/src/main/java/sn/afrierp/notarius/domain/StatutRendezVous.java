package sn.afrierp.notarius.domain;

public enum StatutRendezVous {
    PLANIFIE("Planifie", true),
    CONFIRME("Confirme", true),
    HONORE("Honore", false),
    ANNULE("Annule", false),
    ABSENT("Client absent", false);

    private final String libelle;
    /** Un rendez-vous actif occupe le creneau (detection des conflits). */
    private final boolean actif;

    StatutRendezVous(String libelle, boolean actif) {
        this.libelle = libelle;
        this.actif = actif;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean estActif() {
        return actif;
    }
}
