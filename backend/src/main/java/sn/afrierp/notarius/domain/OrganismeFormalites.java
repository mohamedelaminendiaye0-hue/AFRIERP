package sn.afrierp.notarius.domain;

public enum OrganismeFormalites {
    CONSERVATION_FONCIERE("Conservation fonciere (DGID)", "Mutation / inscription au livre foncier"),
    GREFFE_RCCM("Greffe — RCCM", "Inscription au Registre du commerce et du credit mobilier"),
    PUBLICATION_LEGALE("Journal d'annonces legales", "Publication de l'avis legal");

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
