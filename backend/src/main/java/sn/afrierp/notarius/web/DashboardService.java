package sn.afrierp.notarius.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.compta.ComptaRecords.AlerteConformite;
import sn.afrierp.notarius.compta.ComptaRecords.ControleEtancheite;
import sn.afrierp.notarius.compta.ComptaService;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.MouvementComptable;
import sn.afrierp.notarius.domain.NatureMouvement;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.TypeActe;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.MouvementComptableRepository;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.web.dto.Vues.DossierResume;
import sn.afrierp.notarius.workflow.WorkflowRecords.NotificationRetard;
import sn.afrierp.notarius.workflow.WorkflowService;

/** Agregats de pilotage de la vue Directeur. */
@Service
public class DashboardService {

    public record Kpi(long dossiersActifs, long dossiersSignesCeMois, long encoursFondsClients,
                      long emolumentsAPercevoir, long delaiMoyenJours, int objectifDelaiJours, double tauxRetardGlobal) {
    }

    public record EtapePipeline(StatutDossier statut, String libelle, String acteur, long dossiers, long enRetard) {
    }

    public record ChargeClerc(String userId, String nom, String initiales, long dossiersActifs, long dossiersEnRetard,
                              double tauxRetard, long encoursFonds) {
    }

    public record CaParType(TypeActe typeActe, String libelle, long dossiers, long caPrevisionnel) {
    }

    public record Tableau(Kpi kpi, List<EtapePipeline> pipeline, ControleEtancheite controle,
                          List<AlerteConformite> alertes, List<NotificationRetard> notifications,
                          List<ChargeClerc> charges, List<CaParType> caParType, List<DossierResume> risques,
                          double tauxRetardMaximum) {
    }

    private final DossierRepository dossiers;
    private final MouvementComptableRepository mouvements;
    private final UtilisateurRepository utilisateurs;
    private final ComptaService compta;
    private final WorkflowService workflow;
    private final VueMapper mapper;
    private final Clock clock;

    public DashboardService(DossierRepository dossiers, MouvementComptableRepository mouvements,
                            UtilisateurRepository utilisateurs, ComptaService compta, WorkflowService workflow,
                            VueMapper mapper, Clock clock) {
        this.dossiers = dossiers;
        this.mouvements = mouvements;
        this.utilisateurs = utilisateurs;
        this.compta = compta;
        this.workflow = workflow;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Tableau tableau() {
        LocalDate ref = LocalDate.now(clock);
        List<Dossier> tous = dossiers.findAll();
        List<DossierResume> resumes = mapper.resumes(tous);
        List<DossierResume> actifs = resumes.stream().filter(r -> r.statut() != StatutDossier.CLOSED).toList();
        Map<String, Dossier> parId = tous.stream().collect(Collectors.toMap(Dossier::getId, d -> d));

        /* --- KPI --- */
        YearMonth mois = YearMonth.from(ref);
        long signesCeMois = tous.stream().filter(d -> d.getDateSignature() != null && YearMonth.from(d.getDateSignature()).equals(mois)).count();
        Set<String> honorairesPreleves = mouvements.findAll().stream()
                .filter(m -> m.getNature() == NatureMouvement.HONORAIRES_ETUDE)
                .map(MouvementComptable::getDossierId).collect(Collectors.toSet());
        long emoluments = actifs.stream().filter(r -> !honorairesPreleves.contains(r.id()))
                .mapToLong(r -> workflow.taxationPrevisionnelle(parId.get(r.id())).totaux().produitEtude()).sum();
        List<Dossier> signes = tous.stream().filter(d -> d.getDateSignature() != null).toList();
        long delaiMoyen = signes.isEmpty() ? 0 : Math.round(signes.stream()
                .mapToLong(d -> ChronoUnit.DAYS.between(d.getDateOuverture(), d.getDateSignature())).average().orElse(0));
        long enRetard = actifs.stream().filter(r -> r.retardSignatureJours() > 0 || r.retardStatutJours() > 0).count();
        Kpi kpi = new Kpi(actifs.size(), signesCeMois, compta.compteCollectif().getSoldeBancaire(), emoluments, delaiMoyen,
                Bareme.OBJECTIF_DELAI_TRAITEMENT_JOURS, actifs.isEmpty() ? 0 : (double) enRetard / actifs.size());

        /* --- Pipeline par statut --- */
        Map<StatutDossier, List<DossierResume>> parStatut = new EnumMap<>(StatutDossier.class);
        resumes.forEach(r -> parStatut.computeIfAbsent(r.statut(), s -> new java.util.ArrayList<>()).add(r));
        List<EtapePipeline> pipeline = Arrays.stream(StatutDossier.values()).map(s -> {
            List<DossierResume> liste = parStatut.getOrDefault(s, List.of());
            return new EtapePipeline(s, s.getLibelle(), s.getActeur(), liste.size(),
                    liste.stream().filter(r -> r.retardStatutJours() > 0).count());
        }).toList();

        /* --- Charge par clerc --- */
        List<ChargeClerc> charges = utilisateurs.findByRoleAndActifTrueOrderByNom(Role.CLERC).stream()
                .map(u -> chargeDe(u, actifs))
                .sorted(Comparator.comparingLong(ChargeClerc::dossiersActifs).reversed())
                .toList();

        /* --- CA previsionnel par type d'acte --- */
        Map<TypeActe, long[]> ca = new EnumMap<>(TypeActe.class);
        for (DossierResume r : actifs) {
            long[] cumul = ca.computeIfAbsent(r.typeActe(), t -> new long[2]);
            cumul[0]++;
            cumul[1] += workflow.taxationPrevisionnelle(parId.get(r.id())).totaux().produitEtude();
        }
        List<CaParType> caParType = ca.entrySet().stream()
                .map(e -> new CaParType(e.getKey(), e.getKey().getLibelle(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(CaParType::caPrevisionnel).reversed())
                .toList();

        /* --- Dossiers a risque --- */
        List<DossierResume> risques = actifs.stream()
                .filter(r -> r.statut() == StatutDossier.PENDING_DOCS || r.purgeConditionsEnCours() || r.alerteCdc()
                        || r.retardSignatureJours() > 0 || r.retardStatutJours() > 0)
                .sorted(Comparator.comparingLong(DossierResume::retardStatutJours).reversed())
                .toList();

        return new Tableau(kpi, pipeline, compta.controlerEtancheite(), compta.alertesConformite(),
                workflow.notificationsRetard(tous), charges, caParType, risques, Bareme.OBJECTIF_TAUX_RETARD_MAXIMUM);
    }

    private static ChargeClerc chargeDe(Utilisateur u, List<DossierResume> actifs) {
        List<DossierResume> siens = actifs.stream().filter(r -> r.clercId().equals(u.getId())).toList();
        long enRetard = siens.stream().filter(r -> r.retardStatutJours() > 0 || r.retardSignatureJours() > 0).count();
        return new ChargeClerc(u.getId(), u.nomComplet(), u.getInitiales(), siens.size(), enRetard,
                siens.isEmpty() ? 0 : (double) enRetard / siens.size(),
                siens.stream().mapToLong(DossierResume::soldeFonds).sum());
    }
}
