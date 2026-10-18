package sn.afrierp.notarius.securite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import sn.afrierp.notarius.admin.JournalAdmin;
import sn.afrierp.notarius.admin.PolitiqueSecurite;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.domain.ParametresSecurite;
import sn.afrierp.notarius.domain.TypeEvenementAdmin;
import sn.afrierp.notarius.web.UtilisateurCourant;

import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.web.dto.Vues.UtilisateurVue;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Mot de passe commun aux comptes du jeu de démonstration. */
    public static final String MOT_DE_PASSE_DEMO = "notarius2026";
    private static final String REFUS = "Adresse e-mail ou mot de passe incorrect.";

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final JetonService jetons;
    private final PolitiqueSecurite politique;
    private final JournalAdmin journal;
    private final Clock clock;
    private final boolean demo;

    public AuthController(UtilisateurRepository utilisateurs, PasswordEncoder encodeur, JetonService jetons,
                          PolitiqueSecurite politique, JournalAdmin journal, Clock clock,
                          @Value("${notarius.donnees-demo:true}") boolean demo) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.jetons = jetons;
        this.politique = politique;
        this.journal = journal;
        this.clock = clock;
        this.demo = demo;
    }

    public record Identifiants(@NotBlank String email, @NotBlank String motDePasse) {
    }

    public record Session(String jeton, Instant expiration, UtilisateurVue utilisateur, boolean doitChangerMotDePasse) {
    }

    public record CompteDemo(String email, String motDePasse, String nom, String role, String roleLibelle) {
    }

    public record Refus(String message) {
    }

    public record ChangementMotDePasse(@NotBlank String actuel, @NotBlank String nouveau) {
    }

    /**
     * Connexion. Après N échecs consécutifs (politique de sécurité), le compte est
     * verrouillé quelques minutes ; chaque tentative est tracée au journal.
     */
    @PostMapping("/connexion")
    @Transactional(noRollbackFor = RegleMetierException.class)
    public ResponseEntity<?> connexion(@Valid @RequestBody Identifiants id) {
        String email = id.email().trim();
        Optional<Utilisateur> trouve = utilisateurs.findByEmailIgnoreCase(email);
        if (trouve.isEmpty()) {
            journal.tracer(TypeEvenementAdmin.CONNEXION_ECHOUEE, null, null, email, "Adresse inconnue");
            return refus(REFUS);
        }
        Utilisateur u = trouve.get();
        LocalDateTime maintenant = LocalDateTime.now(clock);
        if (!u.isActif()) {
            journal.tracer(TypeEvenementAdmin.CONNEXION_ECHOUEE, null, u, "Compte désactivé");
            return refus("Ce compte est désactivé. Contactez l'administrateur de la plateforme.");
        }
        if (u.estVerrouille(maintenant)) {
            journal.tracer(TypeEvenementAdmin.CONNEXION_ECHOUEE, null, u, "Compte verrouillé");
            return refus("Compte verrouillé après plusieurs échecs. Réessayez après %s ou contactez l'administrateur."
                    .formatted(u.getVerrouilleJusqua().toLocalTime().withSecond(0).withNano(0)));
        }
        if (u.getMotDePasse() == null || !encodeur.matches(id.motDePasse(), u.getMotDePasse())) {
            ParametresSecurite p = politique.parametres();
            u.setEchecsConnexion(u.getEchecsConnexion() + 1);
            if (u.getEchecsConnexion() >= p.getTentativesAvantVerrouillage()) {
                u.setVerrouilleJusqua(maintenant.plusMinutes(p.getDureeVerrouillageMinutes()));
                u.setEchecsConnexion(0);
                journal.tracer(TypeEvenementAdmin.COMPTE_VERROUILLE, null, u,
                        "%d échecs consécutifs · verrouillé %d min".formatted(p.getTentativesAvantVerrouillage(), p.getDureeVerrouillageMinutes()));
                return refus("Trop de tentatives : compte verrouillé %d minutes.".formatted(p.getDureeVerrouillageMinutes()));
            }
            journal.tracer(TypeEvenementAdmin.CONNEXION_ECHOUEE, null, u, "Mot de passe erroné (%d/%d)"
                    .formatted(u.getEchecsConnexion(), p.getTentativesAvantVerrouillage()));
            return refus(REFUS);
        }
        u.setEchecsConnexion(0);
        u.setVerrouilleJusqua(null);
        u.setDerniereConnexion(maintenant);
        journal.tracer(TypeEvenementAdmin.CONNEXION_REUSSIE, u, u, null);
        return ResponseEntity.ok(session(u));
    }

    /** Changement de son propre mot de passe ; ferme les autres sessions et renvoie une session neuve. */
    @PostMapping("/mot-de-passe")
    @Transactional
    public Session changerMotDePasse(@Valid @RequestBody ChangementMotDePasse c, @UtilisateurCourant Utilisateur courant) {
        // Recharge le compte dans la transaction : l'utilisateur résolu depuis le jeton est détaché.
        Utilisateur moi = utilisateurs.findById(courant.getId()).orElseThrow();
        if (!encodeur.matches(c.actuel(), moi.getMotDePasse())) {
            throw new RegleMetierException("MOT_DE_PASSE_ACTUEL", "Le mot de passe actuel est incorrect.");
        }
        if (c.actuel().equals(c.nouveau())) {
            throw new RegleMetierException("MOT_DE_PASSE_IDENTIQUE", "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        politique.verifier(c.nouveau(), moi);
        moi.setMotDePasse(encodeur.encode(c.nouveau()));
        moi.setDoitChangerMotDePasse(false);
        moi.setVersionJeton(moi.getVersionJeton() + 1);
        journal.tracer(TypeEvenementAdmin.MOT_DE_PASSE_CHANGE, moi, moi, "Autres sessions fermées");
        return session(moi);
    }

    /** Politique de mot de passe, affichée sur l'écran de changement. */
    @GetMapping("/politique")
    public Map<String, Integer> politique() {
        return Map.of("longueurMinimale", politique.parametres().getLongueurMinimale());
    }

    /** Comptes de démonstration affichés sur la page de connexion (désactivé hors démonstration). */
    @GetMapping("/comptes-demo")
    @Transactional(readOnly = true)
    public List<CompteDemo> comptesDemo() {
        if (!demo) {
            return List.of();
        }
        return utilisateurs.findAll().stream()
                .filter(Utilisateur::isActif)
                .filter(u -> !u.isDoitChangerMotDePasse())
                .sorted(Comparator.comparing((Utilisateur u) -> u.getRole().ordinal()).thenComparing(Utilisateur::getId))
                .map(u -> new CompteDemo(u.getEmail(), MOT_DE_PASSE_DEMO, u.nomComplet(), u.getRole().name(),
                        u.getRole().getLibelle()))
                .toList();
    }

    private Session session(Utilisateur u) {
        JetonService.Jeton j = jetons.emettre(u);
        return new Session(j.valeur(), j.expiration(), UtilisateurVue.de(u), u.isDoitChangerMotDePasse());
    }

    private static ResponseEntity<Refus> refus(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Refus(message));
    }
}
