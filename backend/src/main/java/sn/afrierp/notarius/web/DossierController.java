package sn.afrierp.notarius.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.afrierp.notarius.common.RessourceIntrouvableException;
import sn.afrierp.notarius.domain.Bien;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.Intervenant;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.web.dto.Commandes;
import sn.afrierp.notarius.web.dto.Vues.DossierDetail;
import sn.afrierp.notarius.web.dto.Vues.DossierResume;
import sn.afrierp.notarius.workflow.DossierService;
import sn.afrierp.notarius.workflow.WorkflowService;

/**
 * Dossiers et cycle de vie. Chaque action renvoie le dossier recalcule, avec les
 * verdicts des transitions sortantes pour l'utilisateur courant.
 */
@RestController
@RequestMapping("/api/dossiers")
public class DossierController {

    private final DossierRepository dossiers;
    private final UtilisateurRepository utilisateurs;
    private final DossierService dossierService;
    private final WorkflowService workflow;
    private final VueMapper mapper;

    public DossierController(DossierRepository dossiers, UtilisateurRepository utilisateurs,
                             DossierService dossierService, WorkflowService workflow, VueMapper mapper) {
        this.dossiers = dossiers;
        this.utilisateurs = utilisateurs;
        this.dossierService = dossierService;
        this.workflow = workflow;
        this.mapper = mapper;
    }

    private Utilisateur utilisateur(String id) {
        return utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur " + id + " introuvable."));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<DossierResume> lister() {
        return mapper.resumes(dossiers.findAllByOrderByDateOuvertureDesc());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public DossierDetail detail(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.dossier(id), moi);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DossierDetail creer(@Valid @RequestBody Commandes.NouveauDossier cmd, @UtilisateurCourant Utilisateur moi) {
        Bien bien = cmd.bien() == null || cmd.bien().designation() == null || cmd.bien().designation().isBlank() ? null
                : new Bien(cmd.bien().designation(), cmd.bien().titreFoncier(), cmd.bien().adresse(), cmd.bien().commune(),
                cmd.bien().superficieM2(), cmd.bien().usage());
        List<Intervenant> intervenants = cmd.intervenants() == null ? List.of() : cmd.intervenants().stream()
                .map(DossierController::intervenant).toList();
        Dossier d = dossierService.creer(cmd.intitule(), cmd.typeActe(), cmd.montantTransaction(), cmd.prixAcquisition(),
                optionnel(cmd.clercId()), utilisateur(cmd.notaireId()), bien, intervenants, moi);
        return mapper.detail(d, moi);
    }

    private Utilisateur optionnel(String id) {
        return id == null || id.isBlank() ? null : utilisateur(id);
    }

    private static Intervenant intervenant(Commandes.NouvelIntervenant i) {
        Intervenant intervenant = new Intervenant(i.type(), i.nom().trim(), i.qualite(), i.identifiant(), false);
        intervenant.setTelephone(i.telephone());
        intervenant.setEmail(i.email());
        return intervenant;
    }

    @PostMapping("/{id}/transitions")
    @Transactional
    public DossierDetail transition(@PathVariable String id, @Valid @RequestBody Commandes.DemandeTransition cmd,
                                    @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.appliquer(id, cmd.vers(), moi, cmd.motif(), cmd.modeSignature()), moi);
    }

    @PatchMapping("/{id}/pieces/{pieceId}")
    @Transactional
    public DossierDetail piece(@PathVariable String id, @PathVariable Long pieceId, @Valid @RequestBody Commandes.MajPiece cmd,
                               @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.majPiece(id, pieceId, cmd.statut(), cmd.dateEmission(), cmd.commentaire(), moi), moi);
    }

    @PostMapping("/{id}/intervenants")
    @Transactional
    public DossierDetail ajouterIntervenant(@PathVariable String id, @Valid @RequestBody Commandes.NouvelIntervenant cmd,
                                            @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.ajouterIntervenant(id, intervenant(cmd), moi), moi);
    }

    @PostMapping("/{id}/intervenants/{intervenantId}/kyc")
    @Transactional
    public DossierDetail validerKyc(@PathVariable String id, @PathVariable Long intervenantId, @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.validerKyc(id, intervenantId, moi), moi);
    }

    @PostMapping("/{id}/purge/levee")
    @Transactional
    public DossierDetail leverPurge(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.leverPurge(id, moi), moi);
    }

    @PostMapping("/{id}/assignation")
    @Transactional
    public DossierDetail assigner(@PathVariable String id, @Valid @RequestBody Commandes.Assignation cmd,
                                  @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.assigner(id, optionnel(cmd.clercId()), optionnel(cmd.notaireId()), moi), moi);
    }

    @PostMapping("/{id}/visa-principal-clerc")
    @Transactional
    public DossierDetail viser(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.viserProjet(id, moi), moi);
    }

    @PostMapping("/{id}/taxage-definitif")
    @Transactional
    public DossierDetail taxageDefinitif(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.arreterTaxageDefinitif(id, moi), moi);
    }

    @PostMapping("/{id}/gainde")
    @Transactional
    public DossierDetail gainde(@PathVariable String id, @Valid @RequestBody Commandes.MajGainde cmd,
                                @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.majStatutGainde(id, cmd.statut(), cmd.reference(), moi), moi);
    }

    @PostMapping("/{id}/formalites")
    @Transactional
    public DossierDetail deposerFormalite(@PathVariable String id, @Valid @RequestBody Commandes.DepotFormalite cmd,
                                          @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.deposerFormalite(id, cmd.organisme(), cmd.reference(), moi), moi);
    }

    @PostMapping("/{id}/formalites/{organisme}/achevement")
    @Transactional
    public DossierDetail acheverFormalite(@PathVariable String id, @PathVariable OrganismeFormalites organisme,
                                          @UtilisateurCourant Utilisateur moi) {
        return mapper.detail(workflow.acheverFormalite(id, organisme, moi), moi);
    }
}
