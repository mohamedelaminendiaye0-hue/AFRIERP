package sn.afrierp.notarius.domain;

import jakarta.persistence.Column;
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

/** Ligne du journal d'administration : append-only, jamais modifiée ni supprimée. */
@Entity
@Immutable
@Getter
@NoArgsConstructor
public class EvenementAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime horodatage;

    @Enumerated(EnumType.STRING)
    private TypeEvenementAdmin type;

    /** Auteur de l'action (null pour une connexion anonyme échouée). */
    private String acteurId;
    private String acteurNom;
    /** Compte concerné, ou adresse e-mail saisie lors d'un échec de connexion. */
    private String cibleId;
    private String cibleNom;

    @Column(length = 1000)
    private String detail;
    private String adresseIp;

    public EvenementAdmin(LocalDateTime horodatage, TypeEvenementAdmin type, String acteurId, String acteurNom,
                          String cibleId, String cibleNom, String detail, String adresseIp) {
        this.horodatage = horodatage;
        this.type = type;
        this.acteurId = acteurId;
        this.acteurNom = acteurNom;
        this.cibleId = cibleId;
        this.cibleNom = cibleNom;
        this.detail = detail;
        this.adresseIp = adresseIp;
    }
}
