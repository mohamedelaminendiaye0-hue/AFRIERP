package sn.afrierp.notarius.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Preuve jointe à un acte du répertoire (minute numérisée, quittance…). Les
 * métadonnées et le contenu sont stockés séparément : les listes ne chargent pas
 * les fichiers. Une preuve versée ne se modifie ni ne se supprime ; son empreinte
 * SHA-256 permet d'en vérifier l'intégrité.
 */
@Entity
@Immutable
@Getter
@NoArgsConstructor
public class PieceJointe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String dossierId;

    @Enumerated(EnumType.STRING)
    private TypePieceJointe type;

    private String nomFichier;
    private String typeContenu;
    private long taille;
    private String empreinteSha256;
    private String commentaire;

    private LocalDateTime deposeLe;
    private String deposeParId;
    private String deposeParNom;

    public PieceJointe(String dossierId, TypePieceJointe type, String nomFichier, String typeContenu, long taille,
                       String empreinteSha256, String commentaire, LocalDateTime deposeLe, String deposeParId, String deposeParNom) {
        this.dossierId = dossierId;
        this.type = type;
        this.nomFichier = nomFichier;
        this.typeContenu = typeContenu;
        this.taille = taille;
        this.empreinteSha256 = empreinteSha256;
        this.commentaire = commentaire;
        this.deposeLe = deposeLe;
        this.deposeParId = deposeParId;
        this.deposeParNom = deposeParNom;
    }
}
