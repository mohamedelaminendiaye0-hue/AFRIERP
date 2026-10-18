package sn.afrierp.notarius.web;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.bareme.DecompteTaxation;
import sn.afrierp.notarius.bareme.TaxationService;
import sn.afrierp.notarius.compta.ComptaRecords.AlerteConformite;
import sn.afrierp.notarius.compta.ComptaRecords.ControleEtancheite;
import sn.afrierp.notarius.compta.ComptaRecords.LigneReleve;
import sn.afrierp.notarius.compta.ComptaRecords.OrdreVirementCdc;
import sn.afrierp.notarius.compta.ComptaRecords.VerdictVirement;
import sn.afrierp.notarius.compta.ComptaService;
import sn.afrierp.notarius.compta.SousCompte;
import sn.afrierp.notarius.domain.CompteCollectif;
import sn.afrierp.notarius.domain.MouvementComptable;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.TypeActe;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.web.dto.Commandes;
import sn.afrierp.notarius.web.dto.Vues.StatutVue;
import sn.afrierp.notarius.web.dto.Vues.TransitionVue;
import sn.afrierp.notarius.web.dto.Vues.UtilisateurVue;
import sn.afrierp.notarius.web.dto.Vues.WorkflowVue;
import sn.afrierp.notarius.workflow.Transition;
import sn.afrierp.notarius.workflow.WorkflowRecords.NotificationRetard;
import sn.afrierp.notarius.workflow.WorkflowService;

/** Referentiels, pilotage, comptabilite des fonds clients et simulateur. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final UtilisateurRepository utilisateurs;
    private final ComptaService compta;
    private final TaxationService taxation;
    private final WorkflowService workflow;
    private final DashboardService dashboard;

    public ApiController(UtilisateurRepository utilisateurs, ComptaService compta, TaxationService taxation,
                         WorkflowService workflow, DashboardService dashboard) {
        this.utilisateurs = utilisateurs;
        this.compta = compta;
        this.taxation = taxation;
        this.workflow = workflow;
        this.dashboard = dashboard;
    }

    /* --- Referentiels --- */

    @GetMapping("/utilisateurs")
    public List<UtilisateurVue> utilisateurs() {
        return utilisateurs.findAll().stream().map(UtilisateurVue::de).toList();
    }

    @GetMapping("/moi")
    public UtilisateurVue moi(@UtilisateurCourant Utilisateur moi) {
        return UtilisateurVue.de(moi);
    }

    @GetMapping("/workflow")
    public WorkflowVue definitionWorkflow() {
        List<StatutVue> statuts = Arrays.stream(StatutDossier.values())
                .map(s -> new StatutVue(s, s.getLibelle(), s.getActeur(), s.getDescription(), s.ordinal(),
                        Bareme.DELAIS_CIBLES_JOURS.get(s)))
                .toList();
        return new WorkflowVue(statuts, Transition.TOUTES.stream().map(TransitionVue::de).toList());
    }

    @GetMapping("/referentiels/types-acte")
    public List<Map<String, String>> typesActe() {
        return Arrays.stream(TypeActe.values()).map(t -> Map.of("code", t.name(), "libelle", t.getLibelle())).toList();
    }

    /* --- Pilotage --- */

    @GetMapping("/dashboard")
    public DashboardService.Tableau tableauDeBord() {
        return dashboard.tableau();
    }

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public List<NotificationRetard> notifications(@UtilisateurCourant Utilisateur moi) {
        return workflow.notificationsPour(moi);
    }

    /* --- Comptabilite des fonds clients & CDC --- */

    @GetMapping("/compta/compte-collectif")
    public CompteCollectif compteCollectif() {
        return compta.compteCollectif();
    }

    @GetMapping("/compta/controle")
    public ControleEtancheite controle() {
        return compta.controlerEtancheite();
    }

    @GetMapping("/compta/sous-comptes")
    public List<SousCompte> sousComptes() {
        return compta.sousComptes();
    }

    @GetMapping("/compta/alertes")
    public List<AlerteConformite> alertes() {
        return compta.alertesConformite();
    }

    @GetMapping("/compta/sous-comptes/{dossierId}/releve")
    public List<LigneReleve> releve(@PathVariable String dossierId) {
        return compta.releve(dossierId);
    }

    /** Verdict sans effet : sert a desactiver le bouton tant que le virement est interdit. */
    @PostMapping("/compta/virements/verification")
    @Transactional(readOnly = true)
    public VerdictVirement verifierVirement(@Valid @RequestBody Commandes.DemandeVirement cmd, @UtilisateurCourant Utilisateur moi) {
        return compta.autoriserVirement(compta.dossier(cmd.dossierId()), cmd.montant(), cmd.nature(), moi);
    }

    @PostMapping("/compta/virements")
    @Transactional
    public LigneReleve executerVirement(@Valid @RequestBody Commandes.DemandeVirement cmd, @UtilisateurCourant Utilisateur moi) {
        MouvementComptable m = compta.executerVirement(compta.dossier(cmd.dossierId()), cmd.montant(), cmd.nature(),
                cmd.beneficiaire() == null || cmd.beneficiaire().isBlank() ? "beneficiaire non precise" : cmd.beneficiaire().trim(), moi);
        return compta.releve(cmd.dossierId()).stream().filter(l -> l.id().equals(m.getId())).findFirst().orElseThrow();
    }

    @GetMapping("/compta/ordres-cdc/{dossierId}")
    public OrdreVirementCdc ordreCdc(@PathVariable String dossierId) {
        return compta.ordreVirementCdc(dossierId);
    }

    /* --- Simulateur d'emoluments --- */

    @PostMapping("/taxation/simulation")
    public DecompteTaxation simuler(@Valid @RequestBody Commandes.DemandeSimulation cmd) {
        return taxation.simuler(cmd.typeActe(), cmd.assiette(), cmd.honoraireLibre());
    }
}
