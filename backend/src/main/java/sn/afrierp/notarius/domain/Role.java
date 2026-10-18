package sn.afrierp.notarius.domain;

/** Profils de l'etude. Les transitions du workflow sont reservees par profil. */
public enum Role {
    DIRECTEUR("Notaire titulaire"),
    NOTAIRE("Notaire"),
    CLERC("Clerc"),
    COMPTABLE("Comptable"),
    FORMALISTE("Formaliste"),
    ASSISTANT("Secretariat");

    private final String libelle;

    Role(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
