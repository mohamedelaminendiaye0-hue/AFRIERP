package sn.afrierp.notarius.domain;

/**
 * Cycle de vie du dossier (machine a etats). L'ordre de declaration est l'ordre
 * du workflow : il sert a savoir si une etape est franchie.
 */
public enum StatutDossier {
    DRAFT("Création / Brouillon", "Accueil / Clerc",
            "Saisie des informations de base : type d'acte, parties prenantes."),
    PENDING_DOCS("Instruction & Pièces", "Clerc rédacteur",
            "Collecte des pièces (état des droits réels, CNI, statuts). Bloqué si pièces manquantes."),
    DRAFTING("Rédaction en cours", "Clerc rédacteur",
            "Rédaction du projet d'acte."),
    REVIEW("Validation Titulaire", "Notaire titulaire",
            "Relecture, correction et validation finale du projet d'acte."),
    TAXING("Taxage prévisionnel", "Comptable-taxateur",
            "Calcul des frais : émoluments, timbres, enregistrement DGID."),
    FUNDING("En attente de provision", "Comptable / Client",
            "Dépôt des fonds et de la provision sur le compte séquestre de l'étude."),
    READY_TO_SIGN("Prêt pour signature", "Secrétariat / Clerc",
            "Planification du rendez-vous de signature en étude."),
    SIGNED("Signé (minute établie)", "Notaire titulaire",
            "Signature physique ou électronique (clé PKI GAINDE 2000)."),
    POST_ACT("Formalités en cours", "Formaliste",
            "Dépôt à la Conservation foncière (DGID) ou au Greffe (RCCM)."),
    CLOSED("Clôturé & archivé", "Comptable / Archiviste",
            "Taxage définitif, restitution du reliquat, archivage de la minute.");

    private final String libelle;
    private final String acteur;
    private final String description;

    StatutDossier(String libelle, String acteur, String description) {
        this.libelle = libelle;
        this.acteur = acteur;
        this.description = description;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getActeur() {
        return acteur;
    }

    public String getDescription() {
        return description;
    }

    public boolean estAuMoins(StatutDossier autre) {
        return ordinal() >= autre.ordinal();
    }
}
