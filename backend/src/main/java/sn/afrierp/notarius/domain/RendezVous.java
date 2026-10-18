package sn.afrierp.notarius.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Rendez-vous de l'etude : avec un client, rattache ou non a un dossier. */
@Entity
@Table(indexes = @Index(columnList = "debut"))
@Getter
@Setter
@NoArgsConstructor
public class RendezVous {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    private TypeRendezVous type;

    @Enumerated(EnumType.STRING)
    private StatutRendezVous statut = StatutRendezVous.PLANIFIE;

    private LocalDateTime debut;
    private int dureeMinutes;

    @Enumerated(EnumType.STRING)
    private Salle salle;

    /** Notaire recevant le client (obligatoire pour une signature). */
    @ManyToOne
    private Utilisateur notaire;

    /** Clerc ou collaborateur present. */
    @ManyToOne
    private Utilisateur collaborateur;

    @ManyToOne
    private Dossier dossier;

    private String clientNom;
    private String clientTelephone;
    private String objet;

    private String creeParId;
    private LocalDateTime creeLe;
    private int nombreReports;
    private String motifAnnulation;

    public LocalDateTime getFin() {
        return debut.plusMinutes(dureeMinutes);
    }

    public boolean chevauche(LocalDateTime autreDebut, LocalDateTime autreFin) {
        return debut.isBefore(autreFin) && autreDebut.isBefore(getFin());
    }
}
