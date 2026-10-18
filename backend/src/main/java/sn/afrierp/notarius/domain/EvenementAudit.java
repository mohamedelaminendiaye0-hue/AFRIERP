package sn.afrierp.notarius.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Journal d'audit append-only : qui a fait quoi, quand, et depuis quel statut.
 * L'entite est {@link Immutable} et n'expose aucun setter : une ligne ecrite ne
 * peut plus etre modifiee, et aucun endpoint ne permet de la supprimer.
 */
@Entity
@Immutable
@Table(indexes = @Index(columnList = "dossierId"))
@Getter
@NoArgsConstructor
public class EvenementAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String dossierId;
    private LocalDateTime horodatage;

    @Enumerated(EnumType.STRING)
    private TypeEvenement type;

    private String auteurId;

    /** Statut du dossier au moment de l'evenement. */
    @Enumerated(EnumType.STRING)
    private StatutDossier statutDossier;

    private String libelle;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evenement_audit_detail")
    @MapKeyColumn(name = "cle")
    @Column(name = "valeur", length = 1000)
    private Map<String, String> detail = new LinkedHashMap<>();

    public EvenementAudit(String dossierId, LocalDateTime horodatage, TypeEvenement type, String auteurId,
                          StatutDossier statutDossier, String libelle, Map<String, String> detail) {
        this.dossierId = dossierId;
        this.horodatage = horodatage;
        this.type = type;
        this.auteurId = auteurId;
        this.statutDossier = statutDossier;
        this.libelle = libelle;
        if (detail != null) {
            this.detail.putAll(detail);
        }
    }
}
