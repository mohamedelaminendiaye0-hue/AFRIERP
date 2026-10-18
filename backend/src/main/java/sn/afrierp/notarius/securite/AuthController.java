package sn.afrierp.notarius.securite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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

    /** Mot de passe commun aux comptes du jeu de demonstration. */
    public static final String MOT_DE_PASSE_DEMO = "notarius2026";

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;
    private final JetonService jetons;
    private final boolean demo;

    public AuthController(UtilisateurRepository utilisateurs, PasswordEncoder encodeur, JetonService jetons,
                          @Value("${notarius.donnees-demo:true}") boolean demo) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.jetons = jetons;
        this.demo = demo;
    }

    public record Identifiants(@NotBlank String email, @NotBlank String motDePasse) {
    }

    public record Session(String jeton, Instant expiration, UtilisateurVue utilisateur) {
    }

    public record CompteDemo(String email, String motDePasse, String nom, String role, String roleLibelle) {
    }

    public record Refus(String message) {
    }

    @PostMapping("/connexion")
    @Transactional(readOnly = true)
    public ResponseEntity<?> connexion(@Valid @RequestBody Identifiants id) {
        return utilisateurs.findByEmailIgnoreCase(id.email().trim())
                .filter(Utilisateur::isActif)
                .filter(u -> u.getMotDePasse() != null && encodeur.matches(id.motDePasse(), u.getMotDePasse()))
                .<ResponseEntity<?>>map(u -> {
                    JetonService.Jeton j = jetons.emettre(u);
                    return ResponseEntity.ok(new Session(j.valeur(), j.expiration(), UtilisateurVue.de(u)));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new Refus("Adresse e-mail ou mot de passe incorrect.")));
    }

    /** Comptes de demonstration affiches sur la page de connexion (desactive hors demonstration). */
    @GetMapping("/comptes-demo")
    @Transactional(readOnly = true)
    public List<CompteDemo> comptesDemo() {
        if (!demo) {
            return List.of();
        }
        return utilisateurs.findAll().stream()
                .filter(Utilisateur::isActif)
                .sorted(Comparator.comparing((Utilisateur u) -> u.getRole().ordinal()).thenComparing(Utilisateur::getId))
                .map(u -> new CompteDemo(u.getEmail(), MOT_DE_PASSE_DEMO, u.nomComplet(), u.getRole().name(),
                        u.getRole().getLibelle()))
                .toList();
    }
}
