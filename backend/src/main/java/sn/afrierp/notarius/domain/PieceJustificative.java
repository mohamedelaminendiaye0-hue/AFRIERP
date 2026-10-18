package sn.afrierp.notarius.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Piece de la checklist du dossier (module GED). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PieceJustificative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Dossier dossier;

    private int ordre;
    private String libelle;

    @Enumerated(EnumType.STRING)
    private CategoriePiece categorie;

    /** Une piece obligatoire bloque le dossier tant qu'elle n'est pas recue. */
    private boolean obligatoire;

    /**
     * Statut a partir duquel la piece est exigee. Par defaut DRAFTING : la
     * redaction est verrouillee tant qu'elle manque. Certaines pieces (mainlevee,
     * attestation de depot du capital) ne sont exigees qu'a la mise a la signature.
     */
    @Enumerated(EnumType.STRING)
    private StatutDossier exigibleA = StatutDossier.DRAFTING;

    /** Duree de validite en jours a compter de l'emission (ex : etat des droits reels < 3 mois). */
    private Integer validiteJours;

    /** A la charge de quel intervenant / de l'etude. */
    private String responsable;

    @Enumerated(EnumType.STRING)
    private StatutPiece statut = StatutPiece.MANQUANTE;

    private LocalDate dateDemande;
    private LocalDate dateReception;
    /** Date d'emission par l'administration : point de depart de la validite. */
    private LocalDate dateEmission;
    /** Date au-dela de laquelle la piece est perimee (ex : quitus fiscal). */
    private LocalDate dateExpiration;
    private String commentaire;

    public boolean estObtenue() {
        return statut == StatutPiece.RECUE || statut == StatutPiece.VALIDEE;
    }

    public boolean estPerimee(LocalDate dateReference) {
        if (dateExpiration != null && dateExpiration.isBefore(dateReference)) {
            return true;
        }
        LocalDate depart = dateEmission != null ? dateEmission : dateReception;
        return validiteJours != null && depart != null
                && ChronoUnit.DAYS.between(depart, dateReference) > validiteJours;
    }

    /** La piece est-elle exigee pour qu'un dossier se trouve dans {@code statut} ? */
    public boolean estExigiblePour(StatutDossier statut) {
        return obligatoire && statut.estAuMoins(exigibleA);
    }
}
