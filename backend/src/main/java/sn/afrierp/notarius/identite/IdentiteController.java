package sn.afrierp.notarius.identite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.domain.IdentiteEtude;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.IdentiteEtudeRepository;
import sn.afrierp.notarius.web.UtilisateurCourant;

/** Marque blanche : lecture publique (page de connexion), ecriture reservee au super administrateur. */
@RestController
@RequestMapping("/api/identite")
public class IdentiteController {

    static final Set<String> POLICES = Set.of("Source Sans 3", "IBM Plex Sans", "Systeme", "Georgia");
    private static final int LOGO_MAX = 400_000;
    private static final String COULEUR = "^#[0-9a-fA-F]{6}$";

    private final IdentiteEtudeRepository depot;
    private final Clock clock;

    public IdentiteController(IdentiteEtudeRepository depot, Clock clock) {
        this.depot = depot;
        this.clock = clock;
    }

    public record SaisieIdentite(@NotBlank @Size(max = 80) String nom, @Size(max = 120) String sousTitre,
                                 @Size(max = 3) String monogramme,
                                 @NotBlank @Pattern(regexp = COULEUR) String couleurPrincipale,
                                 @NotBlank @Pattern(regexp = COULEUR) String couleurAccent,
                                 @NotBlank String police, String logo, String adresse, String telephone, String email,
                                 String ninea, String mentionPiedDePage) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public IdentiteEtude lire() {
        return depot.findById(IdentiteEtude.ID).orElseGet(IdentiteController::parDefaut);
    }

    @PutMapping
    @Transactional
    public IdentiteEtude enregistrer(@Valid @RequestBody SaisieIdentite s, @UtilisateurCourant Utilisateur moi) {
        if (moi.getRole() != Role.SUPER_ADMIN) {
            throw new RegleMetierException("ROLE_NON_HABILITE", "L'identité de l'étude est gérée par le super administrateur de la plateforme.");
        }
        if (!POLICES.contains(s.police())) {
            throw new RegleMetierException("POLICE_INCONNUE", "Police non proposée : " + s.police());
        }
        if (s.logo() != null && !s.logo().isBlank()) {
            if (!s.logo().startsWith("data:image/png;base64,") && !s.logo().startsWith("data:image/svg+xml;base64,")
                    && !s.logo().startsWith("data:image/jpeg;base64,")) {
                throw new RegleMetierException("LOGO_INVALIDE", "Le logo doit être une image PNG, JPEG ou SVG.");
            }
            if (s.logo().length() > LOGO_MAX) {
                throw new RegleMetierException("LOGO_TROP_LOURD", "Le logo dépasse 300 Ko : réduisez-le avant de l'importer.");
            }
        }
        IdentiteEtude i = depot.findById(IdentiteEtude.ID).orElseGet(IdentiteEtude::new);
        i.setNom(s.nom().trim());
        i.setSousTitre(vide(s.sousTitre()));
        i.setMonogramme(vide(s.monogramme()) == null ? s.nom().trim().substring(0, 1).toUpperCase() : s.monogramme().trim().toUpperCase());
        i.setCouleurPrincipale(s.couleurPrincipale().toUpperCase());
        i.setCouleurAccent(s.couleurAccent().toUpperCase());
        i.setPolice(s.police());
        i.setLogo(vide(s.logo()));
        i.setAdresse(vide(s.adresse()));
        i.setTelephone(vide(s.telephone()));
        i.setEmail(vide(s.email()));
        i.setNinea(vide(s.ninea()));
        i.setMentionPiedDePage(vide(s.mentionPiedDePage()));
        i.setMiseAJourLe(LocalDateTime.now(clock));
        i.setMiseAJourPar(moi.nomComplet());
        return depot.save(i);
    }

    public static IdentiteEtude parDefaut() {
        IdentiteEtude i = new IdentiteEtude();
        i.setNom("Étude Ndoye & Associés");
        i.setSousTitre("Notaires associés · Dakar");
        i.setMonogramme("N");
        i.setCouleurPrincipale("#1E3A5F");
        i.setCouleurAccent("#C9A227");
        i.setPolice("Source Sans 3");
        i.setAdresse("12, avenue Léopold Sédar Senghor, Dakar-Plateau");
        i.setTelephone("+221 33 800 00 00");
        i.setEmail("contact@etude-ndoye.sn");
        return i;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
