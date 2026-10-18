package sn.afrierp.notarius.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import sn.afrierp.notarius.audit.AuditService;
import sn.afrierp.notarius.compta.ComptaRecords.FondsImmobilise;
import sn.afrierp.notarius.compta.ComptaService;
import sn.afrierp.notarius.compta.SousCompte;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.PieceJustificative;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.web.dto.Vues.ActionVue;
import sn.afrierp.notarius.web.dto.Vues.DossierDetail;
import sn.afrierp.notarius.web.dto.Vues.DossierResume;
import sn.afrierp.notarius.web.dto.Vues.EvenementVue;
import sn.afrierp.notarius.web.dto.Vues.IntervenantVue;
import sn.afrierp.notarius.web.dto.Vues.Formalites;
import sn.afrierp.notarius.web.dto.Vues.PieceVue;
import sn.afrierp.notarius.web.dto.Vues.Signature;
import sn.afrierp.notarius.web.dto.Vues.TransitionVue;
import sn.afrierp.notarius.web.dto.Vues.Visa;
import sn.afrierp.notarius.workflow.WorkflowRecords.Delais;
import sn.afrierp.notarius.workflow.WorkflowService;

/** Construit les vues : les champs derives sont calcules, jamais stockes. */
@Component
public class VueMapper {

    private final WorkflowService workflow;
    private final ComptaService compta;
    private final AuditService audit;
    private final UtilisateurRepository utilisateurs;
    private final Clock clock;

    public VueMapper(WorkflowService workflow, ComptaService compta, AuditService audit,
                     UtilisateurRepository utilisateurs, Clock clock) {
        this.workflow = workflow;
        this.compta = compta;
        this.audit = audit;
        this.utilisateurs = utilisateurs;
        this.clock = clock;
    }

    /** Contexte comptable calcule une fois pour toute une liste de dossiers. */
    public record Contexte(Map<String, Long> soldes, Set<String> alertesCdc) {
    }

    public Contexte contexte() {
        Map<String, Long> soldes = compta.sousComptes().stream()
                .collect(Collectors.toMap(SousCompte::dossierId, SousCompte::solde));
        Set<String> alertes = compta.detecterFondsImmobilises().stream()
                .filter(FondsImmobilise::transfertObligatoire)
                .map(f -> f.sousCompte().dossierId())
                .collect(Collectors.toSet());
        return new Contexte(soldes, alertes);
    }

    public List<DossierResume> resumes(List<Dossier> dossiers) {
        Contexte ctx = contexte();
        return dossiers.stream().map(d -> resume(d, ctx)).toList();
    }

    public DossierResume resume(Dossier d, Contexte ctx) {
        LocalDate ref = LocalDate.now(clock);
        List<PieceJustificative> obligatoires = d.getPieces().stream().filter(PieceJustificative::isObligatoire).toList();
        long obtenues = obligatoires.stream().filter(PieceJustificative::estObtenue).count();
        Delais delais = workflow.delais(d);
        long retardSignature = d.getDateSignaturePrevue() != null && d.getDateSignature() == null
                ? Math.max(0, ChronoUnit.DAYS.between(d.getDateSignaturePrevue(), ref)) : 0;
        return new DossierResume(d.getId(), d.getReference(), d.getIntitule(), d.getTypeActe(),
                d.getTypeActe().getLibelle(), d.getStatut(), d.getStatut().getLibelle(), d.getClerc().getId(),
                d.getClerc().nomComplet(), d.getNotaire().getId(), d.getNotaire().nomComplet(), d.getDateOuverture(),
                d.getDateSignaturePrevue(), d.getDateSignature(), d.getMontantTransaction(),
                obligatoires.isEmpty() ? 1 : (double) obtenues / obligatoires.size(),
                ctx.soldes().getOrDefault(d.getId(), 0L), delais.joursDansStatut(), delais.delaiCibleJours(),
                delais.retardJours(), retardSignature, ctx.alertesCdc().contains(d.getId()),
                d.isPurgeConditionsEnCours(), d.getModeSignature(), d.getGaindeStatut());
    }

    public DossierDetail detail(Dossier d, Utilisateur utilisateur) {
        LocalDate ref = LocalDate.now(clock);
        Function<String, String> nom = id -> id == null ? null
                : utilisateurs.findById(id).map(Utilisateur::nomComplet).orElse(id);

        List<PieceVue> pieces = d.getPieces().stream().map(p -> new PieceVue(p.getId(), p.getOrdre(), p.getLibelle(),
                p.getCategorie().name(), p.isObligatoire(), p.getExigibleA(), p.getExigibleA().getLibelle(),
                p.getValiditeJours(), p.getResponsable(), p.getStatut().name(), p.getDateDemande(),
                p.getDateReception(), p.getDateEmission(), p.getDateExpiration(),
                p.estObtenue() && p.estPerimee(ref), p.getCommentaire())).toList();

        List<ActionVue> actions = workflow.transitionsPossibles(d, utilisateur).stream()
                .map(v -> new ActionVue(TransitionVue.de(v.transition()), v.autorisee(), v.blocages()))
                .toList();

        List<EvenementVue> journal = audit.journal(d.getId()).stream()
                .map(e -> EvenementVue.de(e, nom.apply(e.getAuteurId()))).toList();

        Signature signature = d.getModeSignature() == null ? null : new Signature(d.getModeSignature(),
                d.getModeSignature().getLibelle(), d.getDateSignature(), d.getGaindeStatut(),
                d.getGaindeStatut() == null ? null : d.getGaindeStatut().getLibelle(), d.getGaindeDateMiseAJour(),
                d.getGaindeReference());

        return new DossierDetail(
                resume(d, contexte()),
                d.getBien(),
                d.getIntervenants().stream().map(IntervenantVue::de).toList(),
                pieces,
                workflow.taxationPrevisionnelle(d),
                d.getFraisPrevisionnels(),
                d.getVersionBareme(),
                workflow.couverture(d),
                d.getValidationNotaireDate() == null ? null
                        : new Visa(d.getValidationNotaireParId(), nom.apply(d.getValidationNotaireParId()), d.getValidationNotaireDate()),
                d.getCertificationFondsDate() == null ? null
                        : new Visa(d.getCertificationFondsParId(), nom.apply(d.getCertificationFondsParId()), d.getCertificationFondsDate()),
                d.getMontantCertifie(),
                signature,
                d.getFormalitesDateDepot() == null ? null
                        : new Formalites(d.getFormalitesOrganisme(), d.getFormalitesDateDepot(), d.getFormalitesReference()),
                compta.sousCompte(d.getId()).orElse(null),
                compta.releve(d.getId()),
                actions,
                journal);
    }
}
