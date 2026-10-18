package sn.afrierp.notarius.domain;

/** Événements du journal d'administration de la plateforme. */
public enum TypeEvenementAdmin {
    CONNEXION_REUSSIE("Connexion"),
    CONNEXION_ECHOUEE("Échec de connexion"),
    COMPTE_VERROUILLE("Compte verrouillé"),
    COMPTE_DEVERROUILLE("Compte déverrouillé"),
    COMPTE_CREE("Compte créé"),
    COMPTE_MODIFIE("Compte modifié"),
    COMPTE_DESACTIVE("Compte désactivé"),
    COMPTE_REACTIVE("Compte réactivé"),
    MOT_DE_PASSE_REINITIALISE("Mot de passe réinitialisé"),
    MOT_DE_PASSE_CHANGE("Mot de passe changé"),
    SESSIONS_REVOQUEES("Sessions révoquées"),
    IDENTITE_MODIFIEE("Identité de l'étude modifiée"),
    PARAMETRES_MODIFIES("Politique de sécurité modifiée");

    private final String libelle;

    TypeEvenementAdmin(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Événements qui signalent un risque (affichés en alerte). */
    public boolean estSensible() {
        return this == CONNEXION_ECHOUEE || this == COMPTE_VERROUILLE;
    }
}
