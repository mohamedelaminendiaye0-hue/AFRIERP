package sn.afrierp.notarius.domain;

public enum OrganismeFormalites {
    DGID("Impots et domaines (DGID)", "Enregistrement, mutation fiscale ou declaration de succession"),
    CONSERVATION_FONCIERE("Conservation fonciere", "Publication au livre foncier (mutation, inscription, radiation)"),
    GREFFE_RCCM("Greffe — RCCM", "Inscription au Registre du commerce et du credit mobilier"),
    NINEA("Repertoire NINEA (ANSD)", "Immatriculation fiscale et statistique de l'entreprise"),
    PUBLICATION_LEGALE("Journal d'annonces legales", "Publication de l'avis legal / insertion"),
    TRIBUNAL("Tribunal de grande instance", "Homologation judiciaire");

    private final String libelle;
    private final String objet;

    OrganismeFormalites(String libelle, String objet) {
        this.libelle = libelle;
        this.objet = objet;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getObjet() {
        return objet;
    }
}
