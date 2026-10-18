package sn.afrierp.notarius.domain;

/**
 * Suivi des dossiers passes par GAINDE 2000 (signature electronique par cle PKI).
 * Pas d'integration directe a ce stade : le statut est renseigne manuellement.
 */
public enum StatutGainde {
    TRANSMIS("Transmis a GAINDE"),
    VALIDE("Valide par GAINDE"),
    REJETE("Rejete par GAINDE");

    private final String libelle;

    StatutGainde(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
