package sn.afrierp.notarius.workflow;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.audit.AuditService;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.bareme.DecompteTaxation;
import sn.afrierp.notarius.bareme.TaxationService;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.common.RessourceIntrouvableException;
import sn.afrierp.notarius.compta.ComptaService;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.Intervenant;
import sn.afrierp.notarius.domain.ModeSignature;
import sn.afrierp.notarius.domain.MouvementComptable;
import sn.afrierp.notarius.domain.NatureMouvement;
import sn.afrierp.notarius.domain.NiveauAlerte;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.PieceJustificative;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.SensEcriture;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.StatutGainde;
import sn.afrierp.notarius.domain.StatutPiece;
import sn.afrierp.notarius.domain.TypeEvenement;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.workflow.WorkflowRecords.ControlePieces;
import sn.afrierp.notarius.workflow.WorkflowRecords.CouvertureFonds;
import sn.afrierp.notarius.workflow.WorkflowRecords.Delais;
import sn.afrierp.notarius.workflow.WorkflowRecords.NotificationRetard;
import sn.afrierp.notarius.workflow.WorkflowRecords.VerdictTransition;

/**
 * CYCLE DE VIE DU DOSSIER — machine a etats.
 *
 * <p>Un dossier ne transite que selon {@link Transition#TOUTES}. Chaque passage est
 * soumis a {@link #verifier}, qui rend un verdict exhaustif (tous les blocages, pas
 * seulement le premier). L'interface ne re-implemente aucune regle : elle
 * desactive l'action et affiche les motifs du moteur.
 *
 * <ul>
 *   <li>W1 : aucune etape ne peut etre sautee (transitions declarees uniquement) ;</li>
 *   <li>W2 : chaque transition est reservee a un ou plusieurs roles ;</li>
 *   <li>W3 : la redaction est verrouillee tant qu'une piece exigible manque ou est
 *       perimee (ex : etat des droits reels de plus de 3 mois) ;</li>
 *   <li>W4 : la mise a la signature exige une certification comptable : fonds
 *       encaisses sur le sequestre &gt;= frais + prix (selon le type d'acte) ;</li>
 *   <li>W5 : la cloture exige le depot des formalites et la restitution du reliquat.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class WorkflowService {

    private static final Set<Role> SUPERVISION = EnumSet.of(Role.NOTAIRE, Role.DIRECTEUR);
    private static final Set<Role> GESTION_DOSSIER = EnumSet.of(Role.ASSISTANT, Role.CLERC, Role.NOTAIRE, Role.DIRECTEUR);

    private final DossierRepository dossiers;
    private final ComptaService compta;
    private final TaxationService taxation;
    private final AuditService audit;
    private final Clock clock;

    public WorkflowService(DossierRepository dossiers, ComptaService compta, TaxationService taxation,
                           AuditService audit, Clock clock) {
        this.dossiers = dossiers;
        this.compta = compta;
        this.taxation = taxation;
        this.audit = audit;
        this.clock = clock;
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }

    public Dossier dossier(String id) {
        return dossiers.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Dossier " + id + " introuvable."));
    }

    public static Optional<Transition> transition(StatutDossier de, StatutDossier vers) {
        return Transition.TOUTES.stream().filter(t -> t.de() == de && t.vers() == vers).findFirst();
    }

    public static List<Transition> transitionsDepuis(StatutDossier statut) {
        return Transition.TOUTES.stream().filter(t -> t.de() == statut).toList();
    }

    /* ------------------------------------------------------------------ *
     *  Pieces (W3) et couverture des fonds (W4)
     * ------------------------------------------------------------------ */

    public ControlePieces controlerPieces(Dossier dossier, StatutDossier statutVise) {
        LocalDate ref = aujourdhui();
        List<PieceJustificative> exigibles = dossier.getPieces().stream().filter(p -> p.estExigiblePour(statutVise)).toList();
        return new ControlePieces(
                exigibles.stream().filter(p -> !p.estObtenue()).toList(),
                exigibles.stream().filter(p -> p.estObtenue() && p.estPerimee(ref)).toList());
    }

    public DecompteTaxation taxationPrevisionnelle(Dossier dossier) {
        return taxation.simuler(dossier.getTypeActe(), dossier.getMontantTransaction());
    }

    /**
     * Les paiements deja effectues pour le compte du client (droits, tiers) ne
     * reduisent pas la couverture : ils consomment des fonds bien consignes.
     * Seules les restitutions au client viennent en deduction.
     */
    public CouvertureFonds couverture(Dossier dossier) {
        long frais = dossier.getFraisPrevisionnels() != null
                ? dossier.getFraisPrevisionnels()
                : taxationPrevisionnelle(dossier).totaux().totalGeneral();
        long prix = Bareme.SEQUESTRE_PRIX_EXIGE.contains(dossier.getTypeActe()) ? dossier.getMontantTransaction() : 0;
        long encaisse = 0;
        for (MouvementComptable m : compta.mouvementsValides(dossier.getId())) {
            if (m.getSens() == SensEcriture.CREDIT) {
                encaisse += m.getMontant();
            } else if (m.getNature() == NatureMouvement.RESTITUTION_CLIENT) {
                encaisse -= m.getMontant();
            }
        }
        long besoin = frais + prix;
        return new CouvertureFonds(frais, prix, besoin, encaisse, Math.max(0, besoin - encaisse), encaisse >= besoin);
    }

    /* ------------------------------------------------------------------ *
     *  Verdict de transition
     * ------------------------------------------------------------------ */

    public VerdictTransition verifier(Dossier dossier, StatutDossier vers, Utilisateur utilisateur, String motif,
                                      ModeSignature modeSignature) {
        Optional<Transition> trouvee = transition(dossier.getStatut(), vers);
        if (trouvee.isEmpty()) {
            return new VerdictTransition(false, null, List.of(new Blocage("TRANSITION_INTERDITE",
                    "Passage « %s » -> « %s » non prevu : aucune etape ne peut etre sautee."
                            .formatted(dossier.getStatut().getLibelle(), vers.getLibelle()))), null);
        }
        Transition t = trouvee.get();
        List<Blocage> blocages = new ArrayList<>();
        CouvertureFonds couverture = null;

        /* --- W2 : habilitation --- */
        if (!t.roles().contains(utilisateur.getRole())) {
            blocages.add(new Blocage("ROLE_NON_HABILITE", "Action reservee au profil : %s."
                    .formatted(t.roles().stream().map(Role::getLibelle).sorted().collect(Collectors.joining(", ")))));
        } else if (t.notaireDuDossier() && utilisateur.getRole() != Role.DIRECTEUR
                && !utilisateur.getId().equals(dossier.getNotaire().getId())) {
            blocages.add(new Blocage("NOTAIRE_NON_TITULAIRE", "Seul le notaire superviseur du dossier peut realiser cette action."));
        }

        if (t.retour()) {
            if (motif == null || motif.isBlank()) {
                blocages.add(new Blocage("MOTIF_OBLIGATOIRE", "Un retour en arriere doit etre motive (trace au journal)."));
            }
            return new VerdictTransition(blocages.isEmpty(), t, blocages, null);
        }

        /* --- Conditions metier propres au statut vise --- */
        switch (vers) {
            case PENDING_DOCS -> {
                if (dossier.getIntervenants().isEmpty()) {
                    blocages.add(new Blocage("INFOS_INCOMPLETES", "Renseigner au moins une partie a l'acte."));
                }
                if (dossier.getMontantTransaction() <= 0) {
                    blocages.add(new Blocage("INFOS_INCOMPLETES", "Renseigner le prix ou la valeur servant d'assiette."));
                }
            }
            case DRAFTING -> ajouterBlocagesPieces(dossier, StatutDossier.DRAFTING, blocages);
            case FUNDING -> {
                if (dossier.getMontantTransaction() <= 0) {
                    blocages.add(new Blocage("TAXATION_ABSENTE", "Assiette nulle : le taxage previsionnel ne peut pas etre arrete."));
                }
            }
            case READY_TO_SIGN -> {
                ajouterBlocagesPieces(dossier, StatutDossier.READY_TO_SIGN, blocages);
                couverture = couverture(dossier);
                if (!couverture.couvert()) {
                    blocages.add(new Blocage("FONDS_INSUFFISANTS",
                            String.format(Locale.FRANCE, "Fonds consignes insuffisants : %,d FCFA manquent sur le sequestre (frais%s).", couverture.manquant(), couverture.prix() > 0 ? " + prix" : "")));
                }
                List<Intervenant> sansKyc = dossier.getIntervenants().stream().filter(i -> !i.isKycValide()).toList();
                if (dossier.getIntervenants().isEmpty()) {
                    blocages.add(new Blocage("KYC_INCOMPLET", "Aucune partie identifiee : controle KYC / LAB-FT impossible."));
                } else if (!sansKyc.isEmpty()) {
                    blocages.add(new Blocage("KYC_INCOMPLET", "Controle KYC / LAB-FT a realiser : %s."
                            .formatted(sansKyc.stream().map(Intervenant::getNom).collect(Collectors.joining(", ")))));
                }
                if (dossier.isPurgeConditionsEnCours()) {
                    blocages.add(new Blocage("PURGE_EN_COURS", "Purge des droits de preemption / conditions suspensives non achevee."));
                }
            }
            case SIGNED -> {
                if (dossier.getDateSignaturePrevue() == null) {
                    blocages.add(new Blocage("RDV_NON_PLANIFIE", "Planifier le rendez-vous de signature."));
                }
                if (modeSignature == null) {
                    blocages.add(new Blocage("MODE_SIGNATURE_ABSENT", "Choisir le mode de signature (physique ou GAINDE 2000)."));
                }
            }
            case POST_ACT -> {
                if (dossier.getModeSignature() == ModeSignature.ELECTRONIQUE_GAINDE && dossier.getGaindeStatut() != StatutGainde.VALIDE) {
                    blocages.add(new Blocage("GAINDE_NON_VALIDE", "Signature electronique non confirmee : statut GAINDE « %s »."
                            .formatted(dossier.getGaindeStatut() == null ? "non renseigne" : dossier.getGaindeStatut().getLibelle())));
                }
            }
            case CLOSED -> {
                if (dossier.getFormalitesDateDepot() == null) {
                    blocages.add(new Blocage("FORMALITES_NON_DEPOSEES", "Le depot des formalites n'est pas enregistre."));
                }
                long reliquat = compta.solde(dossier.getId());
                if (reliquat != 0) {
                    blocages.add(new Blocage("RELIQUAT_NON_RESTITUE",
                            String.format(Locale.FRANCE, "Reliquat de %,d FCFA a restituer avant archivage (taxage definitif).", reliquat)));
                }
            }
            default -> {
                // REVIEW, TAXING : seules les conditions d'habilitation s'appliquent.
            }
        }
        return new VerdictTransition(blocages.isEmpty(), t, blocages, couverture);
    }

    private void ajouterBlocagesPieces(Dossier dossier, StatutDossier statut, List<Blocage> blocages) {
        ControlePieces controle = controlerPieces(dossier, statut);
        if (!controle.manquantes().isEmpty()) {
            blocages.add(new Blocage("PIECES_MANQUANTES", "%d piece(s) obligatoire(s) manquante(s) : %s."
                    .formatted(controle.manquantes().size(), libelles(controle.manquantes()))));
        }
        if (!controle.perimees().isEmpty()) {
            blocages.add(new Blocage("PIECES_PERIMEES", "%d piece(s) perimee(s), a renouveler : %s."
                    .formatted(controle.perimees().size(), libelles(controle.perimees()))));
        }
    }

    private static String libelles(List<PieceJustificative> pieces) {
        return pieces.stream().map(PieceJustificative::getLibelle).collect(Collectors.joining(", "));
    }

    /**
     * Verdicts des transitions sortantes pour l'utilisateur, a l'usage de
     * l'interface. Le motif et le mode de signature sont des saisies : ils ne
     * sont pas comptes comme blocages ici, mais exiges lors de l'application.
     */
    public List<VerdictTransition> transitionsPossibles(Dossier dossier, Utilisateur utilisateur) {
        return transitionsDepuis(dossier.getStatut()).stream()
                .map(t -> verifier(dossier, t.vers(), utilisateur, "saisie", ModeSignature.PHYSIQUE))
                .toList();
    }

    /* ------------------------------------------------------------------ *
     *  Application d'une transition
     * ------------------------------------------------------------------ */

    /**
     * Applique la transition si le moteur l'autorise. Un refus est trace au journal
     * ({@code TENTATIVE_BLOQUEE}) puis leve une {@link RegleMetierException}.
     */
    @Transactional
    public Dossier appliquer(String dossierId, StatutDossier vers, Utilisateur utilisateur, String motif,
                             ModeSignature modeSignature) {
        Dossier dossier = dossier(dossierId);
        VerdictTransition verdict = verifier(dossier, vers, utilisateur, motif, modeSignature);
        if (!verdict.autorisee()) {
            audit.tracerTentativeBloquee(dossier, utilisateur, "Transition refusee vers « %s »".formatted(vers.getLibelle()),
                    Map.of("vers", vers.name(),
                            "motifs", verdict.blocages().stream().map(Blocage::code).distinct().collect(Collectors.joining(", "))));
            throw new RegleMetierException(verdict.blocages());
        }

        LocalDate date = aujourdhui();
        StatutDossier avant = dossier.getStatut();

        if (avant == StatutDossier.REVIEW && vers == StatutDossier.TAXING) {
            dossier.setValidationNotaireParId(utilisateur.getId());
            dossier.setValidationNotaireDate(date);
            audit.tracer(dossier, TypeEvenement.VALIDATION_NOTAIRE, utilisateur, "Projet d'acte valide par le notaire", null);
        }
        if (avant == StatutDossier.TAXING && vers == StatutDossier.FUNDING) {
            DecompteTaxation decompte = taxationPrevisionnelle(dossier);
            dossier.setFraisPrevisionnels(decompte.totaux().totalGeneral());
            dossier.setVersionBareme(decompte.versionBareme());
        }
        if (avant == StatutDossier.FUNDING && vers == StatutDossier.TAXING) {
            dossier.setFraisPrevisionnels(null);
            dossier.setVersionBareme(null);
        }
        if (vers == StatutDossier.READY_TO_SIGN && verdict.couverture() != null) {
            dossier.setCertificationFondsParId(utilisateur.getId());
            dossier.setCertificationFondsDate(date);
            dossier.setMontantCertifie(verdict.couverture().encaisse());
            audit.tracer(dossier, TypeEvenement.CERTIFICATION_FONDS, utilisateur, "Fonds consignes certifies sur le compte sequestre",
                    Map.of("besoin", Long.toString(verdict.couverture().besoin()),
                            "encaisse", Long.toString(verdict.couverture().encaisse())));
        }
        if (vers == StatutDossier.SIGNED) {
            dossier.setDateSignature(date);
            dossier.setModeSignature(modeSignature);
            if (modeSignature == ModeSignature.ELECTRONIQUE_GAINDE) {
                dossier.setGaindeStatut(StatutGainde.TRANSMIS);
                dossier.setGaindeDateMiseAJour(date);
            }
            audit.tracer(dossier, TypeEvenement.SIGNATURE, utilisateur, "Minute signee — " + modeSignature.getLibelle(), null);
        }
        if (vers == StatutDossier.CLOSED) {
            dossier.setDateCloture(date);
        }

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("avant", avant.name());
        detail.put("apres", vers.name());
        if (motif != null && !motif.isBlank()) {
            detail.put("motif", motif.trim());
        }
        audit.tracer(dossier, TypeEvenement.CHANGEMENT_STATUT, utilisateur,
                "%s au statut « %s »".formatted(verdict.transition().retour() ? "Retour" : "Passage", vers.getLibelle()), detail);

        dossier.setStatut(vers);
        dossier.setDateEntreeStatut(date);
        return dossier;
    }

    /* ------------------------------------------------------------------ *
     *  Actions de suivi
     * ------------------------------------------------------------------ */

    private static void exigerRole(Utilisateur u, Set<Role> roles, String message) {
        if (!roles.contains(u.getRole())) {
            throw new RegleMetierException("ROLE_NON_HABILITE", message);
        }
    }

    private static void exigerNonCloture(Dossier dossier) {
        if (dossier.getStatut() == StatutDossier.CLOSED) {
            throw new RegleMetierException("DOSSIER_CLOTURE", "Dossier cloture et archive : il n'est plus modifiable.");
        }
    }

    /** Reassignation dynamique du clerc redacteur, par le notaire ou le directeur. */
    @Transactional
    public Dossier reassignerClerc(String dossierId, Utilisateur clerc, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerNonCloture(dossier);
        exigerRole(auteur, SUPERVISION, "Seul un notaire peut reassigner un dossier.");
        if (clerc.getRole() != Role.CLERC || !clerc.isActif()) {
            throw new RegleMetierException("CLERC_INVALIDE", "Le dossier doit etre assigne a un clerc actif.");
        }
        if (clerc.getId().equals(dossier.getClerc().getId())) {
            throw new RegleMetierException("DEJA_ASSIGNE", "Ce clerc est deja assigne au dossier.");
        }
        audit.tracer(dossier, TypeEvenement.ASSIGNATION, auteur, "Dossier reassigne a " + clerc.nomComplet(),
                Map.of("avant", dossier.getClerc().getId(), "apres", clerc.getId()));
        dossier.setClerc(clerc);
        return dossier;
    }

    /** Evolution du statut d'une piece de la checklist (GED). */
    @Transactional
    public Dossier majPiece(String dossierId, Long pieceId, StatutPiece statut, LocalDate dateEmission,
                            String commentaire, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerNonCloture(dossier);
        exigerRole(auteur, GESTION_DOSSIER, "Mise a jour des pieces reservee aux clercs, au secretariat et aux notaires.");
        PieceJustificative piece = dossier.getPieces().stream().filter(p -> p.getId().equals(pieceId)).findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Piece " + pieceId + " introuvable."));
        StatutPiece avant = piece.getStatut();
        piece.setStatut(statut);
        if ((statut == StatutPiece.RECUE || statut == StatutPiece.VALIDEE) && piece.getDateReception() == null) {
            piece.setDateReception(aujourdhui());
        }
        if (statut == StatutPiece.MANQUANTE || statut == StatutPiece.DEMANDEE || statut == StatutPiece.REJETEE) {
            piece.setDateReception(null);
            piece.setDateEmission(null);
        }
        if (statut == StatutPiece.DEMANDEE) {
            piece.setDateDemande(aujourdhui());
        }
        if (dateEmission != null) {
            piece.setDateEmission(dateEmission);
        }
        if (commentaire != null) {
            piece.setCommentaire(commentaire.isBlank() ? null : commentaire.trim());
        }
        TypeEvenement type = switch (statut) {
            case RECUE -> TypeEvenement.PIECE_RECUE;
            case VALIDEE -> TypeEvenement.PIECE_VALIDEE;
            case REJETEE -> TypeEvenement.PIECE_REJETEE;
            default -> TypeEvenement.PIECE_MODIFIEE;
        };
        audit.tracer(dossier, type, auteur, piece.getLibelle(), Map.of("avant", avant.name(), "apres", statut.name()));
        return dossier;
    }

    @Transactional
    public Dossier ajouterIntervenant(String dossierId, Intervenant intervenant, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerRole(auteur, GESTION_DOSSIER, "Saisie des parties reservee aux clercs, au secretariat et aux notaires.");
        if (dossier.getStatut().estAuMoins(StatutDossier.SIGNED)) {
            throw new RegleMetierException("ACTE_SIGNE", "Les parties d'un acte signe ne peuvent plus etre modifiees.");
        }
        intervenant.setKycValide(false);
        dossier.ajouterIntervenant(intervenant);
        audit.tracer(dossier, TypeEvenement.PIECE_MODIFIEE, auteur, "Partie ajoutee : " + intervenant.getNom(),
                Map.of("qualite", intervenant.getQualite().name()));
        return dossier;
    }

    /** Validation du controle KYC / LAB-FT d'une partie (clerc ou notaire). */
    @Transactional
    public Dossier validerKyc(String dossierId, Long intervenantId, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerNonCloture(dossier);
        exigerRole(auteur, EnumSet.of(Role.CLERC, Role.NOTAIRE, Role.DIRECTEUR), "Controle KYC reserve aux clercs et aux notaires.");
        Intervenant intervenant = dossier.getIntervenants().stream().filter(i -> i.getId().equals(intervenantId)).findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Partie " + intervenantId + " introuvable."));
        intervenant.setKycValide(true);
        audit.tracer(dossier, TypeEvenement.PIECE_VALIDEE, auteur, "Controle KYC / LAB-FT valide : " + intervenant.getNom(), null);
        return dossier;
    }

    @Transactional
    public Dossier leverPurge(String dossierId, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerRole(auteur, EnumSet.of(Role.CLERC, Role.NOTAIRE, Role.DIRECTEUR), "Action reservee aux clercs et aux notaires.");
        if (!dossier.isPurgeConditionsEnCours()) {
            throw new RegleMetierException("AUCUNE_PURGE", "Aucune purge en cours sur ce dossier.");
        }
        dossier.setPurgeConditionsEnCours(false);
        audit.tracer(dossier, TypeEvenement.PIECE_VALIDEE, auteur, "Purge des droits de preemption / conditions suspensives achevee", null);
        return dossier;
    }

    /** Planification du rendez-vous de signature (secretariat / clerc). */
    @Transactional
    public Dossier planifierSignature(String dossierId, LocalDate date, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        if (dossier.getStatut() != StatutDossier.READY_TO_SIGN) {
            throw new RegleMetierException("STATUT_INCOMPATIBLE", "Le rendez-vous se planifie une fois le dossier pret pour signature.");
        }
        exigerRole(auteur, GESTION_DOSSIER, "Planification reservee au secretariat, aux clercs et aux notaires.");
        if (date == null || date.isBefore(aujourdhui())) {
            throw new RegleMetierException("DATE_INVALIDE", "Choisir une date a venir.");
        }
        audit.tracer(dossier, TypeEvenement.PLANIFICATION_SIGNATURE, auteur, "Rendez-vous de signature planifie",
                Map.of("avant", String.valueOf(dossier.getDateSignaturePrevue()), "apres", date.toString()));
        dossier.setDateSignaturePrevue(date);
        return dossier;
    }

    /** Mise a jour manuelle du statut GAINDE 2000 : pas d'integration directe a ce stade. */
    @Transactional
    public Dossier majStatutGainde(String dossierId, StatutGainde statut, String reference, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        if (dossier.getModeSignature() != ModeSignature.ELECTRONIQUE_GAINDE) {
            throw new RegleMetierException("HORS_GAINDE", "Ce dossier n'a pas ete signe via GAINDE 2000.");
        }
        exigerRole(auteur, EnumSet.of(Role.NOTAIRE, Role.DIRECTEUR, Role.FORMALISTE), "Mise a jour reservee aux notaires et au formaliste.");
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("avant", String.valueOf(dossier.getGaindeStatut()));
        detail.put("apres", statut.name());
        if (reference != null && !reference.isBlank()) {
            detail.put("reference", reference.trim());
            dossier.setGaindeReference(reference.trim());
        }
        audit.tracer(dossier, TypeEvenement.SUIVI_GAINDE, auteur, "Statut GAINDE 2000 : " + statut.getLibelle(), detail);
        dossier.setGaindeStatut(statut);
        dossier.setGaindeDateMiseAJour(aujourdhui());
        return dossier;
    }

    /** Enregistrement du depot des formalites (Conservation fonciere ou Greffe). */
    @Transactional
    public Dossier deposerFormalites(String dossierId, OrganismeFormalites organisme, String reference, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        if (dossier.getStatut() != StatutDossier.POST_ACT) {
            throw new RegleMetierException("STATUT_INCOMPATIBLE", "Les formalites se deposent au statut « Formalites en cours ».");
        }
        exigerRole(auteur, EnumSet.of(Role.FORMALISTE, Role.DIRECTEUR), "Depot reserve au formaliste.");
        dossier.setFormalitesOrganisme(organisme);
        dossier.setFormalitesDateDepot(aujourdhui());
        dossier.setFormalitesReference(reference == null || reference.isBlank() ? null : reference.trim());
        audit.tracer(dossier, TypeEvenement.DEPOT_FORMALITES, auteur,
                "Formalites deposees — " + (organisme == OrganismeFormalites.CONSERVATION_FONCIERE
                        ? "Conservation fonciere (DGID)" : "Greffe (RCCM)"),
                reference == null || reference.isBlank() ? null : Map.of("reference", reference.trim()));
        return dossier;
    }

    /* ------------------------------------------------------------------ *
     *  Delais cibles et notifications de retard
     * ------------------------------------------------------------------ */

    public Delais delais(Dossier dossier) {
        if (dossier.getStatut() == StatutDossier.CLOSED) {
            return new Delais(0, null, 0);
        }
        long jours = Math.max(0, ChronoUnit.DAYS.between(dossier.getDateEntreeStatut(), aujourdhui()));
        Integer cible = Bareme.DELAIS_CIBLES_JOURS.get(dossier.getStatut());
        return new Delais(jours, cible, cible == null ? 0 : Math.max(0, jours - cible));
    }

    /** Retards sur les delais cibles : destinataires = clerc assigne et notaire superviseur. */
    public List<NotificationRetard> notificationsRetard(List<Dossier> liste) {
        return liste.stream()
                .map(d -> {
                    Delais delais = delais(d);
                    if (delais.retardJours() <= 0) {
                        return null;
                    }
                    int cible = delais.delaiCibleJours();
                    NiveauAlerte niveau = delais.retardJours() > cible ? NiveauAlerte.CRITIQUE
                            : delais.retardJours() > 7 ? NiveauAlerte.SERIEUX : NiveauAlerte.VIGILANCE;
                    return new NotificationRetard(d.getId(), d.getReference(), d.getIntitule(), d.getStatut(),
                            delais.joursDansStatut(), cible, delais.retardJours(), d.getClerc().getId(),
                            d.getNotaire().getId(), niveau);
                })
                .filter(n -> n != null)
                .sorted(Comparator.comparingLong(NotificationRetard::retardJours).reversed())
                .toList();
    }

    public List<NotificationRetard> notificationsPour(Utilisateur utilisateur) {
        List<NotificationRetard> toutes = notificationsRetard(dossiers.findAll());
        if (utilisateur.getRole() == Role.DIRECTEUR) {
            return toutes;
        }
        return toutes.stream()
                .filter(n -> n.clercId().equals(utilisateur.getId()) || n.notaireId().equals(utilisateur.getId()))
                .toList();
    }
}
