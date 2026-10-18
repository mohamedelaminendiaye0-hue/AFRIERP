package sn.afrierp.notarius.domain;

public enum TypeRendezVous {
    CONSULTATION("Consultation / premier contact", 45),
    REMISE_PIECES("Remise de pièces", 30),
    LECTURE_PROJET("Lecture du projet d'acte", 60),
    SIGNATURE("Signature de l'acte", 90),
    REMISE_FONDS("Remise ou consignation de fonds", 30),
    AUTRE("Autre", 30);

    private final String libelle;
    private final int dureeParDefautMinutes;

    TypeRendezVous(String libelle, int dureeParDefautMinutes) {
        this.libelle = libelle;
        this.dureeParDefautMinutes = dureeParDefautMinutes;
    }

    public String getLibelle() {
        return libelle;
    }

    public int getDureeParDefautMinutes() {
        return dureeParDefautMinutes;
    }
}
