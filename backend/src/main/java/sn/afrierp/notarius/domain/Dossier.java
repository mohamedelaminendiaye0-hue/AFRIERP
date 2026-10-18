package sn.afrierp.notarius.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unite de travail metier : un acte en cours de vie. Le statut n'est jamais
 * modifie directement : il evolue par {@code WorkflowService}, qui verifie les
 * conditions de chaque transition et trace le passage au journal d'audit.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Dossier {

    @Id
    private String id;

    @Version
    private Long version;

    /** Reference interne de l'etude (ex : "2026-VI-0142"). */
    @Column(unique = true, nullable = false)
    private String reference;

    private String intitule;

    @Enumerated(EnumType.STRING)
    private TypeActe typeActe;

    @Enumerated(EnumType.STRING)
    private StatutDossier statut = StatutDossier.DRAFT;

    /** Date d'entree dans le statut courant : point de depart du delai cible. */
    private LocalDate dateEntreeStatut;

    /** Clerc redacteur assigne. */
    @ManyToOne(optional = false)
    private Utilisateur clerc;

    /** Notaire titulaire superviseur. */
    @ManyToOne(optional = false)
    private Utilisateur notaire;

    private LocalDate dateOuverture;
    private LocalDate dateSignaturePrevue;
    private LocalDate dateSignature;
    private LocalDate dateCloture;

    /** Prix / valeur servant d'assiette a la taxation (XOF, sans decimale). */
    private long montantTransaction;

    @Embedded
    private Bien bien;

    /** Purge des droits de preemption / conditions suspensives non achevee. */
    private boolean purgeConditionsEnCours;

    /* --- Taxage previsionnel fige au passage TAXING -> FUNDING --- */
    private Long fraisPrevisionnels;
    private String versionBareme;

    /* --- Visas des etapes franchies --- */
    private String validationNotaireParId;
    private LocalDate validationNotaireDate;
    private String certificationFondsParId;
    private LocalDate certificationFondsDate;
    private Long montantCertifie;

    /* --- Signature et suivi GAINDE 2000 (saisie manuelle, sans integration) --- */
    @Enumerated(EnumType.STRING)
    private ModeSignature modeSignature;

    @Enumerated(EnumType.STRING)
    private StatutGainde gaindeStatut;

    private LocalDate gaindeDateMiseAJour;
    private String gaindeReference;

    /* --- Formalites post-acte --- */
    @Enumerated(EnumType.STRING)
    private OrganismeFormalites formalitesOrganisme;

    private LocalDate formalitesDateDepot;
    private String formalitesReference;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Intervenant> intervenants = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre")
    private List<PieceJustificative> pieces = new ArrayList<>();

    public void ajouterIntervenant(Intervenant intervenant) {
        intervenant.setDossier(this);
        intervenants.add(intervenant);
    }

    public void ajouterPiece(PieceJustificative piece) {
        piece.setDossier(this);
        piece.setOrdre(pieces.size() + 1);
        pieces.add(piece);
    }
}
