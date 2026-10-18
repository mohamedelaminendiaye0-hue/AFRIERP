package sn.afrierp.notarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Octets d'une preuve, dans une table à part (identifiant = celui de la pièce). */
@Entity
@Immutable
@Getter
@NoArgsConstructor
public class ContenuPieceJointe {

    /** 15 Mo : taille maximale d'une preuve (bytea sous PostgreSQL). */
    public static final int TAILLE_MAX = 15 * 1024 * 1024;

    @Id
    private Long id;

    @Column(length = TAILLE_MAX)
    private byte[] contenu;

    public ContenuPieceJointe(Long id, byte[] contenu) {
        this.id = id;
        this.contenu = contenu;
    }
}
