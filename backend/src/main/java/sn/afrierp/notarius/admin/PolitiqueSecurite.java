package sn.afrierp.notarius.admin;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.domain.ParametresSecurite;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.ParametresSecuriteRepository;

/** Politique de mots de passe et de sessions ; génération des mots de passe provisoires. */
@Service
public class PolitiqueSecurite {

    /** Alphabet sans caractères ambigus (0/O, 1/l/I) : le mot de passe provisoire se dicte au téléphone. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEA = new SecureRandom();

    private final ParametresSecuriteRepository depot;

    public PolitiqueSecurite(ParametresSecuriteRepository depot) {
        this.depot = depot;
    }

    @Transactional(readOnly = true)
    public ParametresSecurite parametres() {
        return depot.findById(ParametresSecurite.ID).orElseGet(ParametresSecurite::new);
    }

    /** Mot de passe provisoire : groupes de 4 caractères, au moins une lettre et un chiffre. */
    public String provisoire() {
        int longueur = Math.max(12, parametres().getLongueurMinimale());
        StringBuilder sb = new StringBuilder();
        do {
            sb.setLength(0);
            for (int i = 0; i < longueur; i++) {
                if (i > 0 && i % 4 == 0) {
                    sb.append('-');
                }
                sb.append(ALPHABET.charAt(ALEA.nextInt(ALPHABET.length())));
            }
        } while (!sb.chars().anyMatch(Character::isDigit) || !sb.chars().anyMatch(Character::isLetter));
        return sb.toString();
    }

    /** Vérifie un mot de passe choisi par l'utilisateur ; lève les manquements à la politique. */
    public void verifier(String motDePasse, Utilisateur u) {
        ParametresSecurite p = parametres();
        List<Blocage> manquements = new ArrayList<>();
        if (motDePasse == null || motDePasse.length() < p.getLongueurMinimale()) {
            manquements.add(new Blocage("MOT_DE_PASSE_COURT",
                    "Le mot de passe doit contenir au moins %d caractères.".formatted(p.getLongueurMinimale())));
        }
        if (motDePasse != null && (motDePasse.chars().noneMatch(Character::isLetter) || motDePasse.chars().noneMatch(Character::isDigit))) {
            manquements.add(new Blocage("MOT_DE_PASSE_SIMPLE", "Le mot de passe doit mêler des lettres et des chiffres."));
        }
        if (motDePasse != null && u.getEmail() != null
                && motDePasse.toLowerCase().contains(u.getEmail().split("@")[0].toLowerCase())) {
            manquements.add(new Blocage("MOT_DE_PASSE_PERSONNEL", "Le mot de passe ne doit pas reprendre votre identifiant."));
        }
        if (!manquements.isEmpty()) {
            throw new RegleMetierException(manquements);
        }
    }
}
