package sn.afrierp.notarius.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.afrierp.notarius.domain.EvenementAdmin;
import sn.afrierp.notarius.domain.ParametresSecurite;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.TypeEvenementAdmin;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.EvenementAdminRepository;
import sn.afrierp.notarius.repository.ParametresSecuriteRepository;
import sn.afrierp.notarius.web.UtilisateurCourant;

/**
 * Espace du super administrateur (accès restreint au profil SUPER_ADMIN par la
 * configuration de sécurité) : comptes, politique de sécurité, journal et état
 * de la plateforme. Le super administrateur n'intervient jamais sur les actes.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CompteService comptes;
    private final PolitiqueSecurite politique;
    private final ParametresSecuriteRepository parametres;
    private final EvenementAdminRepository evenements;
    private final DossierRepository dossiers;
    private final JournalAdmin journal;
    private final Clock clock;
    private final String urlBase;
    private final boolean demo;

    public AdminController(CompteService comptes, PolitiqueSecurite politique, ParametresSecuriteRepository parametres,
                           EvenementAdminRepository evenements, DossierRepository dossiers, JournalAdmin journal, Clock clock,
                           @Value("${spring.datasource.url:}") String urlBase,
                           @Value("${notarius.donnees-demo:true}") boolean demo) {
        this.comptes = comptes;
        this.politique = politique;
        this.parametres = parametres;
        this.evenements = evenements;
        this.dossiers = dossiers;
        this.journal = journal;
        this.clock = clock;
        this.urlBase = urlBase;
        this.demo = demo;
    }

    /* ------------------------------------------------------------------ *
     *  Tableau de bord
     * ------------------------------------------------------------------ */

    public record EvenementVue(Long id, LocalDateTime horodatage, TypeEvenementAdmin type, String typeLibelle, boolean sensible,
                               String acteurId, String acteurNom, String cibleId, String cibleNom, String detail, String adresseIp) {

        static EvenementVue de(EvenementAdmin e) {
            return new EvenementVue(e.getId(), e.getHorodatage(), e.getType(), e.getType().getLibelle(), e.getType().estSensible(),
                    e.getActeurId(), e.getActeurNom(), e.getCibleId(), e.getCibleNom(), e.getDetail(), e.getAdresseIp());
        }
    }

    public record ParRole(Role role, String libelle, long actifs) {
    }

    public record Systeme(String version, String base, boolean donneesDemo, LocalDateTime demarreLe, long dossiers,
                          String dureeFonctionnement) {
    }

    public record TableauAdmin(long comptesActifs, long comptesDesactives, long comptesVerrouilles, long motsDePasseProvisoires,
                               long jamaisConnectes, long connexions24h, long echecs24h, List<ParRole> parRole,
                               List<EvenementVue> derniersEvenements, List<CompteService.CompteVue> aSurveiller,
                               ParametresSecurite politique, Systeme systeme) {
    }

    @GetMapping("/tableau")
    @Transactional(readOnly = true)
    public TableauAdmin tableau() {
        List<CompteService.CompteVue> tous = comptes.lister();
        List<CompteService.CompteVue> actifs = tous.stream().filter(CompteService.CompteVue::actif).toList();
        LocalDateTime hier = LocalDateTime.now(clock).minusHours(24);
        Map<Role, Long> parRole = actifs.stream().collect(Collectors.groupingBy(CompteService.CompteVue::role, Collectors.counting()));
        List<ParRole> roles = Arrays.stream(Role.values())
                .map(r -> new ParRole(r, r.getLibelle(), parRole.getOrDefault(r, 0L)))
                .toList();
        List<CompteService.CompteVue> aSurveiller = actifs.stream()
                .filter(c -> c.verrouille() || c.doitChangerMotDePasse() || c.derniereConnexion() == null)
                .toList();
        long debut = ManagementFactory.getRuntimeMXBean().getStartTime();
        LocalDateTime demarre = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(debut), clock.getZone());
        Duration duree = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        Systeme systeme = new Systeme(getClass().getPackage().getImplementationVersion() != null
                ? getClass().getPackage().getImplementationVersion() : "0.3.0",
                urlBase.contains("postgresql") ? "PostgreSQL" : urlBase.contains("h2") ? "H2 (mémoire)" : urlBase,
                demo, demarre, dossiers.count(), "%d h %02d min".formatted(duree.toHours(), duree.toMinutesPart()));
        return new TableauAdmin(actifs.size(), tous.size() - actifs.size(),
                actifs.stream().filter(CompteService.CompteVue::verrouille).count(),
                actifs.stream().filter(CompteService.CompteVue::doitChangerMotDePasse).count(),
                actifs.stream().filter(c -> c.derniereConnexion() == null).count(),
                evenements.countByTypeAndHorodatageAfter(TypeEvenementAdmin.CONNEXION_REUSSIE, hier),
                evenements.countByTypeAndHorodatageAfter(TypeEvenementAdmin.CONNEXION_ECHOUEE, hier)
                        + evenements.countByTypeAndHorodatageAfter(TypeEvenementAdmin.COMPTE_VERROUILLE, hier),
                roles, journal(null, null, 8), aSurveiller, politique.parametres(), systeme);
    }

    /* ------------------------------------------------------------------ *
     *  Comptes
     * ------------------------------------------------------------------ */

    @GetMapping("/comptes")
    public List<CompteService.CompteVue> comptes() {
        return comptes.lister();
    }

    @PostMapping("/comptes")
    @ResponseStatus(HttpStatus.CREATED)
    public CompteService.CompteEtMotDePasse creer(@RequestBody CompteService.SaisieCompte s, @UtilisateurCourant Utilisateur moi) {
        return comptes.creer(s, moi);
    }

    @PutMapping("/comptes/{id}")
    public CompteService.CompteVue modifier(@PathVariable String id, @RequestBody CompteService.SaisieCompte s,
                                            @UtilisateurCourant Utilisateur moi) {
        return comptes.modifier(id, s, moi);
    }

    @PostMapping("/comptes/{id}/desactivation")
    public CompteService.CompteVue desactiver(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return comptes.desactiver(id, moi);
    }

    @PostMapping("/comptes/{id}/reactivation")
    public CompteService.CompteVue reactiver(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return comptes.reactiver(id, moi);
    }

    @PostMapping("/comptes/{id}/reinitialisation")
    public CompteService.CompteEtMotDePasse reinitialiser(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return comptes.reinitialiser(id, moi);
    }

    @PostMapping("/comptes/{id}/deverrouillage")
    public CompteService.CompteVue deverrouiller(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return comptes.deverrouiller(id, moi);
    }

    @PostMapping("/comptes/{id}/revocation")
    public CompteService.CompteVue revoquer(@PathVariable String id, @UtilisateurCourant Utilisateur moi) {
        return comptes.revoquerSessions(id, moi);
    }

    /* ------------------------------------------------------------------ *
     *  Politique de sécurité
     * ------------------------------------------------------------------ */

    public record SaisieParametres(@Min(8) @Max(64) int longueurMinimale, @Min(1) @Max(24) int dureeSessionHeures,
                                   @Min(3) @Max(20) int tentativesAvantVerrouillage, @Min(5) @Max(1440) int dureeVerrouillageMinutes,
                                   boolean changementObligatoire) {
    }

    @GetMapping("/parametres")
    public ParametresSecurite parametres() {
        return politique.parametres();
    }

    @PutMapping("/parametres")
    @Transactional
    public ParametresSecurite enregistrerParametres(@Valid @RequestBody SaisieParametres s, @UtilisateurCourant Utilisateur moi) {
        ParametresSecurite p = parametres.findById(ParametresSecurite.ID).orElseGet(ParametresSecurite::new);
        String avant = resume(p);
        p.setLongueurMinimale(s.longueurMinimale());
        p.setDureeSessionHeures(s.dureeSessionHeures());
        p.setTentativesAvantVerrouillage(s.tentativesAvantVerrouillage());
        p.setDureeVerrouillageMinutes(s.dureeVerrouillageMinutes());
        p.setChangementObligatoire(s.changementObligatoire());
        p.setMiseAJourLe(LocalDateTime.now(clock));
        p.setMiseAJourPar(moi.nomComplet());
        parametres.save(p);
        journal.tracer(TypeEvenementAdmin.PARAMETRES_MODIFIES, moi, null, null, avant + " → " + resume(p));
        return p;
    }

    private static String resume(ParametresSecurite p) {
        return "%d car. · session %d h · %d essais · verrou %d min%s".formatted(p.getLongueurMinimale(), p.getDureeSessionHeures(),
                p.getTentativesAvantVerrouillage(), p.getDureeVerrouillageMinutes(), p.isChangementObligatoire() ? " · provisoire" : "");
    }

    /* ------------------------------------------------------------------ *
     *  Journal
     * ------------------------------------------------------------------ */

    public record TypeVue(TypeEvenementAdmin code, String libelle) {
    }

    @GetMapping("/journal/types")
    public List<TypeVue> types() {
        return Arrays.stream(TypeEvenementAdmin.values()).map(t -> new TypeVue(t, t.getLibelle())).toList();
    }

    @GetMapping("/journal")
    @Transactional(readOnly = true)
    public List<EvenementVue> journal(@RequestParam(required = false) TypeEvenementAdmin type,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "300") int limite) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limite, 1), 1000));
        List<EvenementAdmin> lignes = type == null ? evenements.findAllByOrderByHorodatageDescIdDesc(page)
                : evenements.findByTypeOrderByHorodatageDescIdDesc(type, page);
        Function<String, String> norm = x -> x == null ? "" : java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String recherche = norm.apply(q).trim();
        return lignes.stream()
                .filter(e -> recherche.isEmpty() || norm.apply(String.join(" ", String.valueOf(e.getActeurNom()),
                        String.valueOf(e.getCibleNom()), String.valueOf(e.getDetail()), String.valueOf(e.getAdresseIp()))).contains(recherche))
                .map(EvenementVue::de)
                .toList();
    }
}
