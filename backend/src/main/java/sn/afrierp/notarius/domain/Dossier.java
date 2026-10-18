package sn.afrierp.notarius.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

    /** Clerc redacteur assigne (null tant que le dossier attend son assignation). */
    @ManyToOne
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

    /** Dossier d'origine (ex : l'affectation hypothecaire dont on donne mainlevee). */
    private String dossierLieId;

    /** Prix d'acquisition du bien par le vendeur : base de la taxe sur la plus-value. */
    private Long prixAcquisition;

    @Embedded
    private Bien bien;

    /** Purge des droits de preemption / conditions suspensives non achevee. */
    private boolean purgeConditionsEnCours;

    /* --- Taxage previsionnel fige au passage TAXING -> FUNDING --- */
    private Long fraisPrevisionnels;
    private String versionBareme;

    /* --- Visas des etapes franchies --- */
    /** Visa du principal clerc sur le projet d'acte (dossiers complexes, avant soumission au notaire). */
    private String visaPrincipalClercParId;
    private LocalDate visaPrincipalClercDate;

    private String validationNotaireParId;
    private LocalDate validationNotaireDate;
    private String certificationFondsParId;
    private LocalDate certificationFondsDate;
    private Long montantCertifie;

    /* --- Signature et suivi GAINDE 2000 (saisie manuelle, sans integration) --- */
    @Enumerated(EnumType.STRING)
    private ModeSignature modeSignature;

    /** Notaire ayant appose sa signature et son sceau. */
    private String notaireSignataireId;
    /** Notaire engageant sa responsabilite : le signataire, ou son titulaire s'il est salarie. */
    private String notaireResponsableId;

    @Enumerated(EnumType.STRING)
    private StatutGainde gaindeStatut;

    private LocalDate gaindeDateMiseAJour;
    private String gaindeReference;

    /* --- Formalites post-acte (formaliste) --- */
    @ElementCollection
    @CollectionTable(name = "dossier_formalite")
    private List<Formalite> formalites = new ArrayList<>();

    /* --- Taxage definitif (comptable-taxateur) --- */
    private String taxageDefinitifParId;
    private LocalDate taxageDefinitifDate;
    private Long fraisDefinitifs;

    /* --- Minute archivee (archiviste) --- */
    /** Numero d'ordre au repertoire des actes, attribue a la signature (ex : "2026/0001"). */
    @Column(unique = true)
    private String numeroRepertoire;
    private String archiveParId;
    private LocalDate dateArchivage;
    /** Emplacement physique de la minute (liasse, rayonnage, coffre). */
    private String emplacementArchive;
    /** Fin de la duree legale minimale de conservation (75 ans). */
    private LocalDate conservationJusquau;

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

    public java.util.Optional<Formalite> formalite(OrganismeFormalites organisme) {
        return formalites.stream().filter(f -> f.getOrganisme() == organisme).findFirst();
    }

    public void ajouterPiece(PieceJustificative piece) {
        piece.setDossier(this);
        piece.setOrdre(pieces.size() + 1);
        pieces.add(piece);
    }
}
