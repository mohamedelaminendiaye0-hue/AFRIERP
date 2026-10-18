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
import sn.afrierp.notarius.domain.Formalite;
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

    private static final Set<Role> GESTION_DOSSIER = EnumSet.of(Role.ASSISTANT, Role.CLERC, Role.PRINCIPAL_CLERC,
            Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE, Role.NOTAIRE_SALARIE);
    private static final Set<Role> JURIDIQUE = EnumSet.of(Role.CLERC, Role.PRINCIPAL_CLERC,
            Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE, Role.NOTAIRE_SALARIE);

    private final DossierRepository dossiers;
    private final ComptaService compta;
    private final TaxationService taxation;
    private final AuditService audit;
    private final RendezVousService rendezVous;
    private final Clock clock;

    public WorkflowService(DossierRepository dossiers, ComptaService compta, TaxationService taxation,
                           AuditService audit, RendezVousService rendezVous, Clock clock) {
        this.dossiers = dossiers;
        this.compta = compta;
        this.taxation = taxation;
        this.audit = audit;
        this.rendezVous = rendezVous;
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
        return taxation.simuler(dossier.getTypeActe(), dossier.getMontantTransaction(), 0, dossier.getPrixAcquisition());
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
        // Prix (vente, cession, adjudication) ou capital (constitution) a sequestrer en plus des frais.
        long prix = Bareme.parametres(dossier.getTypeActe()).sequestre() != Bareme.Sequestre.AUCUN
                ? dossier.getMontantTransaction() : 0;
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
        } else if (t.notaireDuDossier() && !utilisateur.getRole().estNotaireEnTitre()
                && !utilisateur.getId().equals(dossier.getNotaire().getId())) {
            blocages.add(new Blocage("NOTAIRE_NON_SUPERVISEUR",
                    "Seul le notaire superviseur du dossier (ou un notaire en titre) peut realiser cette action."));
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
                if (dossier.getClerc() == null) {
                    blocages.add(new Blocage("CLERC_NON_ASSIGNE", "Assigner un clerc redacteur au dossier (principal clerc ou notaire)."));
                }
                if (dossier.getIntervenants().isEmpty()) {
                    blocages.add(new Blocage("INFOS_INCOMPLETES", "Renseigner au moins une partie a l'acte."));
                }
                if (dossier.getTypeActe().aUneAssiette() && dossier.getMontantTransaction() <= 0) {
                    blocages.add(new Blocage("INFOS_INCOMPLETES",
                            "Renseigner l'assiette de l'acte : " + dossier.getTypeActe().getAssietteLibelle().toLowerCase() + "."));
                }
            }
            case DRAFTING -> ajouterBlocagesPieces(dossier, StatutDossier.DRAFTING, blocages);
            case REVIEW -> {
                if (dossier.getTypeActe().estComplexe() && dossier.getVisaPrincipalClercDate() == null) {
                    blocages.add(new Blocage("VISA_PRINCIPAL_CLERC_ABSENT",
                            "Dossier complexe (%s) : le projet d'acte doit etre vise par le principal clerc avant soumission au notaire."
                                    .formatted(dossier.getTypeActe().getLibelle().toLowerCase())));
                }
            }
            case FUNDING -> {
                if (dossier.getTypeActe().aUneAssiette() && dossier.getMontantTransaction() <= 0) {
                    blocages.add(new Blocage("TAXATION_ABSENTE", "Assiette nulle : le taxage previsionnel ne peut pas etre arrete."));
                }
            }
            case READY_TO_SIGN -> {
                ajouterBlocagesPieces(dossier, StatutDossier.READY_TO_SIGN, blocages);
                couverture = couverture(dossier);
                if (!couverture.couvert()) {
                    String objet = switch (Bareme.parametres(dossier.getTypeActe()).sequestre()) {
                        case PRIX -> "frais + prix";
                        case CAPITAL -> "frais + capital a liberer";
                        default -> "frais";
                    };
                    blocages.add(new Blocage("FONDS_INSUFFISANTS",
                            String.format(Locale.FRANCE, "Fonds consignes insuffisants : %,d FCFA manquent sur le sequestre (%s).", couverture.manquant(), objet)));
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
                if (rendezVous.signatureActive(dossier.getId()).isEmpty()) {
                    blocages.add(new Blocage("RDV_NON_PLANIFIE", "Aucun rendez-vous de signature planifie (module Rendez-vous)."));
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
                List<OrganismeFormalites> enAttente = formalitesNonAchevees(dossier);
                if (!enAttente.isEmpty()) {
                    blocages.add(new Blocage("FORMALITES_INCOMPLETES", "Formalites non achevees : %s."
                            .formatted(enAttente.stream().map(OrganismeFormalites::getLibelle).collect(Collectors.joining(", ")))));
                }
                if (dossier.getTaxageDefinitifDate() == null) {
                    blocages.add(new Blocage("TAXAGE_DEFINITIF_ABSENT", "Le comptable-taxateur doit arreter le taxage definitif."));
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

    /** Formalites requises pour le type d'acte et pas encore achevees par l'administration. */
    public static List<OrganismeFormalites> formalitesNonAchevees(Dossier dossier) {
        return Bareme.parametres(dossier.getTypeActe()).formalites().stream()
                .filter(o -> dossier.formalite(o).map(f -> !f.estAchevee()).orElse(true))
                .toList();
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

        if (vers == StatutDossier.PENDING_DOCS || (avant == StatutDossier.REVIEW && vers == StatutDossier.DRAFTING)) {
            // Le projet sera retravaille : le visa du principal clerc devra etre redonne.
            dossier.setVisaPrincipalClercParId(null);
            dossier.setVisaPrincipalClercDate(null);
        }
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
            Utilisateur responsable = utilisateur.notaireEngage();
            dossier.setDateSignature(date);
            dossier.setModeSignature(modeSignature);
            dossier.setNotaireSignataireId(utilisateur.getId());
            dossier.setNotaireResponsableId(responsable.getId());
            if (modeSignature == ModeSignature.ELECTRONIQUE_GAINDE) {
                dossier.setGaindeStatut(StatutGainde.TRANSMIS);
                dossier.setGaindeDateMiseAJour(date);
            }
            Map<String, String> signature = new LinkedHashMap<>();
            signature.put("signataire", utilisateur.nomComplet() + " (" + utilisateur.getRole().getLibelle() + ")");
            if (responsable != utilisateur) {
                signature.put("responsabilite", responsable.nomComplet() + " — notaire titulaire");
            }
            audit.tracer(dossier, TypeEvenement.SIGNATURE, utilisateur, "Minute signee et scellee — " + modeSignature.getLibelle(), signature);
            rendezVous.marquerSignatureHonoree(dossier);
        }
        if (vers == StatutDossier.CLOSED) {
            String annee = String.valueOf(date.getYear());
            long rang = dossiers.countByNumeroRepertoireStartingWith(annee + "/") + 1;
            LocalDate depart = dossier.getDateSignature() != null ? dossier.getDateSignature() : date;
            dossier.setDateCloture(date);
            dossier.setNumeroRepertoire("%s/%04d".formatted(annee, rang));
            dossier.setArchiveParId(utilisateur.getId());
            dossier.setDateArchivage(date);
            dossier.setConservationJusquau(depart.plusYears(Bareme.DUREE_CONSERVATION_MINUTES_ANS));
            audit.tracer(dossier, TypeEvenement.ARCHIVAGE_MINUTE, utilisateur,
                    "Minute archivee — repertoire n° " + dossier.getNumeroRepertoire(),
                    Map.of("conservationJusquau", dossier.getConservationJusquau().toString()));
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

    /**
     * Assignation du dossier. Le principal clerc et les notaires en titre affectent le
     * clerc redacteur (planning de production) ; seuls les notaires en titre designent
     * le notaire superviseur. Chaque changement est trace au journal.
     */
    @Transactional
    public Dossier assigner(String dossierId, Utilisateur clerc, Utilisateur notaire, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerNonCloture(dossier);
        exigerRole(auteur, Role.ENCADREMENT, "L'assignation des dossiers est reservee au principal clerc et aux notaires en titre.");
        boolean change = false;
        if (clerc != null) {
            if (clerc.getRole() != Role.CLERC && clerc.getRole() != Role.PRINCIPAL_CLERC || !clerc.isActif()) {
                throw new RegleMetierException("CLERC_INVALIDE", "Le dossier doit etre confie a un clerc actif.");
            }
            String avant = dossier.getClerc() == null ? "non assigne" : dossier.getClerc().getId();
            if (!avant.equals(clerc.getId())) {
                audit.tracer(dossier, TypeEvenement.ASSIGNATION, auteur, "Clerc redacteur : " + clerc.nomComplet(),
                        Map.of("role", "CLERC", "avant", avant, "apres", clerc.getId()));
                dossier.setClerc(clerc);
                change = true;
            }
        }
        if (notaire != null) {
            if (!auteur.getRole().estNotaireEnTitre()) {
                throw new RegleMetierException("ROLE_NON_HABILITE", "Seul un notaire en titre designe le notaire superviseur.");
            }
            if (!notaire.getRole().estNotaire() || !notaire.isActif()) {
                throw new RegleMetierException("NOTAIRE_INVALIDE", "Le superviseur doit etre un notaire actif.");
            }
            if (dossier.getStatut().estAuMoins(StatutDossier.SIGNED)) {
                throw new RegleMetierException("ACTE_SIGNE", "L'acte est signe : le notaire instrumentaire ne peut plus changer.");
            }
            if (!notaire.getId().equals(dossier.getNotaire().getId())) {
                audit.tracer(dossier, TypeEvenement.ASSIGNATION, auteur, "Notaire superviseur : " + notaire.nomComplet(),
                        Map.of("role", "NOTAIRE", "avant", dossier.getNotaire().getId(), "apres", notaire.getId()));
                dossier.setNotaire(notaire);
                change = true;
            }
        }
        if (!change) {
            throw new RegleMetierException("AUCUN_CHANGEMENT", "Ces collaborateurs sont deja assignes au dossier.");
        }
        return dossier;
    }

    /** Visa du principal clerc sur le projet d'acte d'un dossier complexe. */
    @Transactional
    public Dossier viserProjet(String dossierId, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerRole(auteur, EnumSet.of(Role.PRINCIPAL_CLERC, Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE),
                "Le visa du projet est donne par le principal clerc.");
        if (dossier.getStatut() != StatutDossier.DRAFTING) {
            throw new RegleMetierException("STATUT_INCOMPATIBLE", "Le projet se vise pendant la redaction.");
        }
        if (dossier.getVisaPrincipalClercDate() != null) {
            throw new RegleMetierException("DEJA_VISE", "Le projet a deja ete vise.");
        }
        dossier.setVisaPrincipalClercParId(auteur.getId());
        dossier.setVisaPrincipalClercDate(aujourdhui());
        audit.tracer(dossier, TypeEvenement.VISA_PRINCIPAL_CLERC, auteur, "Projet d'acte vise par le principal clerc", null);
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
        exigerRole(auteur, JURIDIQUE, "Controle KYC reserve aux clercs et aux notaires.");
        Intervenant intervenant = dossier.getIntervenants().stream().filter(i -> i.getId().equals(intervenantId)).findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Partie " + intervenantId + " introuvable."));
        intervenant.setKycValide(true);
        audit.tracer(dossier, TypeEvenement.PIECE_VALIDEE, auteur, "Controle KYC / LAB-FT valide : " + intervenant.getNom(), null);
        return dossier;
    }

    @Transactional
    public Dossier leverPurge(String dossierId, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerRole(auteur, JURIDIQUE, "Action reservee aux clercs et aux notaires.");
        if (!dossier.isPurgeConditionsEnCours()) {
            throw new RegleMetierException("AUCUNE_PURGE", "Aucune purge en cours sur ce dossier.");
        }
        dossier.setPurgeConditionsEnCours(false);
        audit.tracer(dossier, TypeEvenement.PIECE_VALIDEE, auteur, "Purge des droits de preemption / conditions suspensives achevee", null);
        return dossier;
    }

    /** Mise a jour manuelle du statut GAINDE 2000 : pas d'integration directe a ce stade. */
    @Transactional
    public Dossier majStatutGainde(String dossierId, StatutGainde statut, String reference, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        if (dossier.getModeSignature() != ModeSignature.ELECTRONIQUE_GAINDE) {
            throw new RegleMetierException("HORS_GAINDE", "Ce dossier n'a pas ete signe via GAINDE 2000.");
        }
        exigerRole(auteur, EnumSet.of(Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE, Role.NOTAIRE_SALARIE, Role.FORMALISTE),
                "Mise a jour reservee aux notaires et au formaliste.");
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

    private static final Set<Role> FORMALITES = EnumSet.of(Role.FORMALISTE, Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE);

    /** Depot d'une formalite aupres de l'administration (formaliste). */
    @Transactional
    public Dossier deposerFormalite(String dossierId, OrganismeFormalites organisme, String reference, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        if (dossier.getStatut() != StatutDossier.POST_ACT) {
            throw new RegleMetierException("STATUT_INCOMPATIBLE", "Les formalites se deposent au statut « Formalites en cours ».");
        }
        exigerRole(auteur, FORMALITES, "Depot reserve au formaliste.");
        if (dossier.formalite(organisme).isPresent()) {
            throw new RegleMetierException("DEJA_DEPOSEE", "Formalite deja deposee : " + organisme.getLibelle() + ".");
        }
        String ref = reference == null || reference.isBlank() ? null : reference.trim();
        dossier.getFormalites().add(new Formalite(organisme, aujourdhui(), ref, null));
        audit.tracer(dossier, TypeEvenement.DEPOT_FORMALITES, auteur, "Formalite deposee — " + organisme.getLibelle(),
                ref == null ? null : Map.of("reference", ref));
        return dossier;
    }

    /** Achevement d'une formalite : mutation faite, inscription prise, avis publie. */
    @Transactional
    public Dossier acheverFormalite(String dossierId, OrganismeFormalites organisme, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerNonCloture(dossier);
        exigerRole(auteur, FORMALITES, "Suivi des formalites reserve au formaliste.");
        Formalite f = dossier.formalite(organisme)
                .orElseThrow(() -> new RegleMetierException("NON_DEPOSEE", "Formalite non deposee : " + organisme.getLibelle() + "."));
        if (f.estAchevee()) {
            throw new RegleMetierException("DEJA_ACHEVEE", "Formalite deja achevee.");
        }
        f.setDateAchevement(aujourdhui());
        audit.tracer(dossier, TypeEvenement.ACHEVEMENT_FORMALITE, auteur,
                "Formalite achevee — " + organisme.getLibelle() + " (" + organisme.getObjet().toLowerCase() + ")", null);
        return dossier;
    }

    /** Taxage definitif par le comptable-taxateur, prealable a la cloture. */
    @Transactional
    public Dossier arreterTaxageDefinitif(String dossierId, Utilisateur auteur) {
        Dossier dossier = dossier(dossierId);
        exigerRole(auteur, EnumSet.of(Role.COMPTABLE_TAXATEUR, Role.NOTAIRE_TITULAIRE),
                "Le taxage definitif est arrete par le comptable-taxateur.");
        if (!dossier.getStatut().estAuMoins(StatutDossier.SIGNED) || dossier.getStatut() == StatutDossier.CLOSED) {
            throw new RegleMetierException("STATUT_INCOMPATIBLE", "Le taxage definitif s'arrete apres la signature, avant la cloture.");
        }
        long definitif = taxationPrevisionnelle(dossier).totaux().totalGeneral();
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("fraisDefinitifs", Long.toString(definitif));
        if (dossier.getFraisPrevisionnels() != null) {
            detail.put("ecartPrevisionnel", Long.toString(definitif - dossier.getFraisPrevisionnels()));
        }
        detail.put("reliquat", Long.toString(compta.solde(dossierId)));
        dossier.setFraisDefinitifs(definitif);
        dossier.setTaxageDefinitifParId(auteur.getId());
        dossier.setTaxageDefinitifDate(aujourdhui());
        audit.tracer(dossier, TypeEvenement.TAXAGE_DEFINITIF, auteur, "Taxage definitif arrete", detail);
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
                            delais.joursDansStatut(), cible, delais.retardJours(), d.getClerc() == null ? null : d.getClerc().getId(),
                            d.getNotaire().getId(), niveau);
                })
                .filter(n -> n != null)
                .sorted(Comparator.comparingLong(NotificationRetard::retardJours).reversed())
                .toList();
    }

    public List<NotificationRetard> notificationsPour(Utilisateur utilisateur) {
        List<NotificationRetard> toutes = notificationsRetard(dossiers.findAll());
        if (Role.ENCADREMENT.contains(utilisateur.getRole())) {
            return toutes;
        }
        return toutes.stream()
                .filter(n -> utilisateur.getId().equals(n.clercId()) || n.notaireId().equals(utilisateur.getId()))
                .toList();
    }
}
