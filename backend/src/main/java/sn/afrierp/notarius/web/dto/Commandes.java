package sn.afrierp.notarius.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import sn.afrierp.notarius.domain.ModeSignature;
import sn.afrierp.notarius.domain.NatureMouvement;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.QualiteIntervenant;
import sn.afrierp.notarius.domain.Salle;
import sn.afrierp.notarius.domain.StatutRendezVous;
import sn.afrierp.notarius.domain.TypeRendezVous;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.StatutGainde;
import sn.afrierp.notarius.domain.StatutPiece;
import sn.afrierp.notarius.domain.TypeActe;
import sn.afrierp.notarius.domain.TypePersonne;
import sn.afrierp.notarius.domain.UsageBien;

/** Corps des requetes de l'API. */
public final class Commandes {

    private Commandes() {
    }

    public record NouvelIntervenant(@NotNull TypePersonne type, @NotBlank String nom, @NotNull QualiteIntervenant qualite,
                                    String identifiant, String telephone, String email) {
    }

    public record NouveauBien(String designation, String titreFoncier, String adresse, String commune,
                              Integer superficieM2, UsageBien usage) {
    }

    /** {@code clercId} facultatif : sans lui, le dossier rejoint la file « a assigner ». */
    public record NouveauDossier(@NotBlank String intitule, @NotNull TypeActe typeActe,
                                 @PositiveOrZero long montantTransaction, @PositiveOrZero Long prixAcquisition,
                                 String clercId, @NotBlank String notaireId, String dossierLieId, NouveauBien bien,
                                 List<@Valid NouvelIntervenant> intervenants) {
    }

    public record DemandeTransition(@NotNull StatutDossier vers, String motif, ModeSignature modeSignature) {
    }

    public record MajPiece(@NotNull StatutPiece statut, LocalDate dateEmission, String commentaire) {
    }

    /** Au moins l'un des deux : clerc redacteur et / ou notaire superviseur. */
    public record Assignation(String clercId, String notaireId) {
    }

    public record NouveauRendezVous(@NotNull TypeRendezVous type, @NotNull LocalDateTime debut,
                                    @Positive int dureeMinutes, @NotNull Salle salle, String notaireId,
                                    String collaborateurId, String dossierId, String clientNom,
                                    String clientTelephone, String objet) {
    }

    public record Report(@NotNull LocalDateTime debut, Integer dureeMinutes) {
    }

    public record ChangementStatutRdv(@NotNull StatutRendezVous statut, String motif) {
    }

    public record MajGainde(@NotNull StatutGainde statut, String reference) {
    }

    public record DepotFormalite(@NotNull OrganismeFormalites organisme, String reference) {
    }

    public record DemandeVirement(@NotBlank String dossierId, long montant, @NotNull NatureMouvement nature,
                                  String beneficiaire) {
    }

    public record DemandeSimulation(@NotNull TypeActe typeActe, @PositiveOrZero long assiette,
                                    @PositiveOrZero long honoraireLibre, @PositiveOrZero Long prixAcquisition) {
    }
}
