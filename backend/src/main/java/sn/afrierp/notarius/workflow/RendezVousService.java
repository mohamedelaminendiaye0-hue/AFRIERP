package sn.afrierp.notarius.workflow;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.audit.AuditService;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.common.RessourceIntrouvableException;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.RendezVous;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.Salle;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.StatutRendezVous;
import sn.afrierp.notarius.domain.TypeEvenement;
import sn.afrierp.notarius.domain.TypeRendezVous;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.RendezVousRepository;

/**
 * PRISE DE RENDEZ-VOUS — agenda de l'etude.
 *
 * <ul>
 *   <li>R1 : un rendez-vous se tient pendant les heures d'ouverture, a une date a venir ;</li>
 *   <li>R2 : aucun chevauchement pour un meme notaire, un meme collaborateur ou une salle partagee ;</li>
 *   <li>R3 : un rendez-vous de signature porte sur un dossier « Pret pour signature », est tenu par un
 *       notaire et fixe la date de signature prevue du dossier ; un seul peut etre actif par dossier.</li>
 * </ul>
 * Comme le workflow, la verification rend tous les blocages a la fois.
 */
@Service
@Transactional(readOnly = true)
public class RendezVousService {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", Locale.FRANCE);
    private static final Set<Role> PLANIFICATION_SIGNATURE = EnumSet.of(Role.ASSISTANT, Role.CLERC, Role.PRINCIPAL_CLERC,
            Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE, Role.NOTAIRE_SALARIE);

    /** Contenu d'une demande de rendez-vous (creation ou report). */
    public record Demande(TypeRendezVous type, LocalDateTime debut, int dureeMinutes, Salle salle, Utilisateur notaire,
                          Utilisateur collaborateur, Dossier dossier, String clientNom, String clientTelephone,
                          String objet) {
    }

    private final RendezVousRepository repository;
    private final AuditService audit;
    private final Clock clock;

    public RendezVousService(RendezVousRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    public RendezVous rendezVous(Long id) {
        return repository.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Rendez-vous " + id + " introuvable."));
    }

    public List<RendezVous> periode(LocalDateTime du, LocalDateTime au) {
        return repository.findByDebutBetweenOrderByDebut(du, au);
    }

    public List<RendezVous> duDossier(String dossierId) {
        return repository.findByDossier_IdOrderByDebutDesc(dossierId);
    }

    public Optional<RendezVous> signatureActive(String dossierId) {
        return duDossier(dossierId).stream()
                .filter(r -> r.getType() == TypeRendezVous.SIGNATURE && r.getStatut().estActif())
                .findFirst();
    }

    /** Rendez-vous actifs du jour ou l'utilisateur est attendu. */
    public List<RendezVous> duJour(Utilisateur u) {
        LocalDateTime debutJour = LocalDateTime.now(clock).toLocalDate().atStartOfDay();
        return periode(debutJour, debutJour.plusDays(1)).stream()
                .filter(r -> r.getStatut().estActif())
                .filter(r -> estConcerne(r, u))
                .toList();
    }

    private static boolean estConcerne(RendezVous r, Utilisateur u) {
        return (r.getNotaire() != null && r.getNotaire().getId().equals(u.getId()))
                || (r.getCollaborateur() != null && r.getCollaborateur().getId().equals(u.getId()))
                || Role.ENCADREMENT.contains(u.getRole()) || u.getRole() == Role.ASSISTANT;
    }

    /* ------------------------------------------------------------------ *
     *  Verification (R1 - R3)
     * ------------------------------------------------------------------ */

    public List<Blocage> verifier(Demande d, Long ignorerId) {
        List<Blocage> blocages = new ArrayList<>();
        if (d.type() == null || d.debut() == null || d.salle() == null) {
            blocages.add(new Blocage("SAISIE_INCOMPLETE", "Type, date et lieu du rendez-vous sont obligatoires."));
            return blocages;
        }
        LocalDateTime fin = d.debut().plusMinutes(d.dureeMinutes());

        /* --- R1 : horaires --- */
        if (!d.debut().isAfter(LocalDateTime.now(clock))) {
            blocages.add(new Blocage("DATE_PASSEE", "Le rendez-vous doit être fixé à une date à venir."));
        }
        if (d.dureeMinutes() < 15 || d.dureeMinutes() > 240) {
            blocages.add(new Blocage("DUREE_INVALIDE", "La durée doit être comprise entre 15 minutes et 4 heures."));
        }
        if (!Bareme.JOURS_OUVRES.contains(d.debut().getDayOfWeek())) {
            blocages.add(new Blocage("HORS_JOURS_OUVRES", "L'étude reçoit du lundi au vendredi."));
        } else if (d.debut().toLocalTime().isBefore(Bareme.OUVERTURE_ETUDE)
                || fin.toLocalTime().isAfter(Bareme.FERMETURE_ETUDE) || !fin.toLocalDate().equals(d.debut().toLocalDate())) {
            blocages.add(new Blocage("HORS_HORAIRES", "L'étude reçoit de %s à %s.".formatted(Bareme.OUVERTURE_ETUDE, Bareme.FERMETURE_ETUDE)));
        }

        /* --- Participants --- */
        if (d.notaire() == null && d.collaborateur() == null) {
            blocages.add(new Blocage("SANS_INTERLOCUTEUR", "Désigner le notaire ou le collaborateur qui reçoit."));
        }
        if (d.notaire() != null && !d.notaire().getRole().estNotaire()) {
            blocages.add(new Blocage("NOTAIRE_INVALIDE", d.notaire().nomComplet() + " n'est pas notaire."));
        }
        if (d.dossier() == null && (d.clientNom() == null || d.clientNom().isBlank())) {
            blocages.add(new Blocage("CLIENT_ABSENT", "Indiquer le client attendu ou rattacher un dossier."));
        }

        /* --- R3 : signature --- */
        if (d.type() == TypeRendezVous.SIGNATURE) {
            if (d.dossier() == null) {
                blocages.add(new Blocage("DOSSIER_REQUIS", "Une signature se rattache à un dossier."));
            } else {
                if (d.dossier().getStatut() != StatutDossier.READY_TO_SIGN) {
                    blocages.add(new Blocage("DOSSIER_NON_PRET", "Le dossier %s est « %s » : la signature se planifie au statut « Prêt pour signature »."
                            .formatted(d.dossier().getReference(), d.dossier().getStatut().getLibelle())));
                }
                signatureActive(d.dossier().getId()).filter(r -> !r.getId().equals(ignorerId)).ifPresent(r ->
                        blocages.add(new Blocage("SIGNATURE_DEJA_PLANIFIEE",
                                "Une signature est déjà planifiée le " + FORMAT.format(r.getDebut()) + " : la reporter plutôt.")));
            }
            if (d.notaire() == null) {
                blocages.add(new Blocage("NOTAIRE_REQUIS", "La signature est reçue par un notaire."));
            }
            if (d.salle() == Salle.BUREAU_CLERC) {
                blocages.add(new Blocage("SALLE_INADAPTEE", "Une signature se tient en salle des signatures, au bureau du notaire ou en visioconférence."));
            }
        }

        /* --- R2 : conflits d'agenda --- */
        LocalDateTime jour = d.debut().toLocalDate().atStartOfDay();
        for (RendezVous r : periode(jour, jour.plusDays(1))) {
            if (!r.getStatut().estActif() || r.getId().equals(ignorerId) || !r.chevauche(d.debut(), fin)) {
                continue;
            }
            String creneau = "%s (%s, %s)".formatted(r.getType().getLibelle(), FORMAT.format(r.getDebut()), r.getDureeMinutes() + " min");
            if (d.notaire() != null && r.getNotaire() != null && r.getNotaire().getId().equals(d.notaire().getId())) {
                blocages.add(new Blocage("CONFLIT_NOTAIRE", d.notaire().nomComplet() + " est déjà pris : " + creneau + "."));
            }
            if (d.collaborateur() != null && r.getCollaborateur() != null && r.getCollaborateur().getId().equals(d.collaborateur().getId())) {
                blocages.add(new Blocage("CONFLIT_COLLABORATEUR", d.collaborateur().nomComplet() + " est déjà pris : " + creneau + "."));
            }
            if (d.salle().estPartagee() && r.getSalle() == d.salle()) {
                blocages.add(new Blocage("CONFLIT_SALLE", d.salle().getLibelle() + " est occupée : " + creneau + "."));
            }
        }
        return blocages;
    }

    /* ------------------------------------------------------------------ *
     *  Cycle du rendez-vous
     * ------------------------------------------------------------------ */

    @Transactional
    public RendezVous creer(Demande d, Utilisateur auteur) {
        if (d.type() == TypeRendezVous.SIGNATURE && !PLANIFICATION_SIGNATURE.contains(auteur.getRole())) {
            throw new RegleMetierException("ROLE_NON_HABILITE", "La signature est planifiée par le secrétariat, les clercs ou les notaires.");
        }
        List<Blocage> blocages = verifier(d, null);
        if (!blocages.isEmpty()) {
            throw new RegleMetierException(blocages);
        }
        RendezVous r = new RendezVous();
        r.setType(d.type());
        appliquer(r, d);
        r.setCreeParId(auteur.getId());
        r.setCreeLe(LocalDateTime.now(clock));
        r = repository.save(r);
        if (r.getDossier() != null) {
            if (r.getType() == TypeRendezVous.SIGNATURE) {
                r.getDossier().setDateSignaturePrevue(r.getDebut().toLocalDate());
            }
            tracer(r, auteur, "Rendez-vous planifié — " + r.getType().getLibelle(), null);
        }
        return r;
    }

    private static void appliquer(RendezVous r, Demande d) {
        r.setDebut(d.debut());
        r.setDureeMinutes(d.dureeMinutes());
        r.setSalle(d.salle());
        r.setNotaire(d.notaire());
        r.setCollaborateur(d.collaborateur());
        r.setDossier(d.dossier());
        r.setClientNom(d.clientNom() == null || d.clientNom().isBlank()
                ? (d.dossier() == null ? null : d.dossier().getIntitule()) : d.clientNom().trim());
        r.setClientTelephone(d.clientTelephone());
        r.setObjet(d.objet());
    }

    /** Report : nouveau creneau, memes participants, re-verification complete. */
    @Transactional
    public RendezVous reporter(Long id, LocalDateTime nouveauDebut, Integer nouvelleDuree, Utilisateur auteur) {
        RendezVous r = rendezVous(id);
        exigerActif(r);
        Demande d = new Demande(r.getType(), nouveauDebut, nouvelleDuree == null ? r.getDureeMinutes() : nouvelleDuree,
                r.getSalle(), r.getNotaire(), r.getCollaborateur(), r.getDossier(), r.getClientNom(), r.getClientTelephone(), r.getObjet());
        List<Blocage> blocages = verifier(d, r.getId());
        if (!blocages.isEmpty()) {
            throw new RegleMetierException(blocages);
        }
        LocalDateTime ancien = r.getDebut();
        r.setDebut(nouveauDebut);
        r.setDureeMinutes(d.dureeMinutes());
        r.setStatut(StatutRendezVous.PLANIFIE);
        r.setNombreReports(r.getNombreReports() + 1);
        if (r.getType() == TypeRendezVous.SIGNATURE && r.getDossier() != null) {
            r.getDossier().setDateSignaturePrevue(nouveauDebut.toLocalDate());
        }
        tracer(r, auteur, "Rendez-vous reporté — " + r.getType().getLibelle(),
                Map.of("avant", FORMAT.format(ancien), "apres", FORMAT.format(nouveauDebut)));
        return r;
    }

    @Transactional
    public RendezVous changerStatut(Long id, StatutRendezVous statut, String motif, Utilisateur auteur) {
        RendezVous r = rendezVous(id);
        exigerActif(r);
        switch (statut) {
            case CONFIRME -> {
                if (r.getStatut() == StatutRendezVous.CONFIRME) {
                    throw new RegleMetierException("DEJA_CONFIRME", "Rendez-vous déjà confirmé.");
                }
            }
            case HONORE, ABSENT -> {
                if (r.getDebut().isAfter(LocalDateTime.now(clock))) {
                    throw new RegleMetierException("PAS_ENCORE_TENU", "Le rendez-vous n'a pas encore eu lieu.");
                }
                if (statut == StatutRendezVous.HONORE && r.getType() == TypeRendezVous.SIGNATURE) {
                    throw new RegleMetierException("SIGNATURE_PAR_WORKFLOW",
                            "Une signature est constatée par la transition « Signer la minute » du dossier.");
                }
            }
            case ANNULE -> {
                if (motif == null || motif.isBlank()) {
                    throw new RegleMetierException("MOTIF_OBLIGATOIRE", "Indiquer le motif de l'annulation.");
                }
                r.setMotifAnnulation(motif.trim());
                if (r.getType() == TypeRendezVous.SIGNATURE && r.getDossier() != null) {
                    r.getDossier().setDateSignaturePrevue(null);
                }
            }
            default -> throw new RegleMetierException("STATUT_INVALIDE", "Changement de statut non prévu.");
        }
        r.setStatut(statut);
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("statut", statut.name());
        if (motif != null && !motif.isBlank()) {
            detail.put("motif", motif.trim());
        }
        tracer(r, auteur, "Rendez-vous " + statut.getLibelle().toLowerCase() + " — " + r.getType().getLibelle(), detail);
        return r;
    }

    /** Appele par le workflow lors de la signature de la minute. */
    @Transactional
    public void marquerSignatureHonoree(Dossier dossier) {
        signatureActive(dossier.getId()).ifPresent(r -> r.setStatut(StatutRendezVous.HONORE));
    }

    private static void exigerActif(RendezVous r) {
        if (!r.getStatut().estActif()) {
            throw new RegleMetierException("RDV_CLOS", "Ce rendez-vous est " + r.getStatut().getLibelle().toLowerCase() + ".");
        }
    }

    private void tracer(RendezVous r, Utilisateur auteur, String libelle, Map<String, String> detail) {
        if (r.getDossier() == null) {
            return;
        }
        Map<String, String> d = new LinkedHashMap<>();
        d.put("creneau", FORMAT.format(r.getDebut()));
        d.put("lieu", r.getSalle().getLibelle());
        if (detail != null) {
            d.putAll(detail);
        }
        audit.tracer(r.getDossier(), r.getType() == TypeRendezVous.SIGNATURE ? TypeEvenement.PLANIFICATION_SIGNATURE
                : TypeEvenement.RENDEZ_VOUS, auteur, libelle, d);
    }
}
