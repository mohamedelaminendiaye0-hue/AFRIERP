package sn.afrierp.notarius.domain;

/**
 * Cycle de vie du dossier (machine a etats). L'ordre de declaration est l'ordre
 * du workflow : il sert a savoir si une etape est franchie.
 */
public enum StatutDossier {
    DRAFT("Creation / Brouillon", "Accueil / Clerc",
            "Saisie des informations de base : type d'acte, parties prenantes."),
    PENDING_DOCS("Instruction & Pieces", "Clerc redacteur",
            "Collecte des pieces (etat des droits reels, CNI, statuts). Bloque si pieces manquantes."),
    DRAFTING("Redaction en cours", "Clerc redacteur",
            "Redaction du projet d'acte."),
    REVIEW("Validation Titulaire", "Notaire titulaire",
            "Relecture, correction et validation finale du projet d'acte."),
    TAXING("Taxage previsionnel", "Comptable-taxateur",
            "Calcul des frais : emoluments, timbres, enregistrement DGID."),
    FUNDING("En attente de provision", "Comptable / Client",
            "Depot des fonds et de la provision sur le compte sequestre de l'etude."),
    READY_TO_SIGN("Pret pour signature", "Secretariat / Clerc",
            "Planification du rendez-vous de signature en etude."),
    SIGNED("Signe (minute etablie)", "Notaire titulaire",
            "Signature physique ou electronique (cle PKI GAINDE 2000)."),
    POST_ACT("Formalites en cours", "Formaliste",
            "Depot a la Conservation fonciere (DGID) ou au Greffe (RCCM)."),
    CLOSED("Cloture & archive", "Comptable / Archiviste",
            "Taxage definitif, restitution du reliquat, archivage de la minute.");

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
