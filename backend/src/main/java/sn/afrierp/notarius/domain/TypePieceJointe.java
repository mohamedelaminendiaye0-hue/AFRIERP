package sn.afrierp.notarius.domain;

/** Nature d'une preuve jointe à un acte inscrit au répertoire. */
public enum TypePieceJointe {
    MINUTE_SIGNEE("Minute signée (copie numérisée)"),
    QUITTANCE_ENREGISTREMENT("Quittance d'enregistrement"),
    ATTESTATION_FORMALITE("Attestation de formalité"),
    PIECE_ANNEXEE("Pièce annexée à l'acte"),
    AUTRE("Autre justificatif");

    private final String libelle;

    TypePieceJointe(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
