package sn.afrierp.notarius.securite;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import sn.afrierp.notarius.repository.UtilisateurRepository;

/**
 * Un jeton n'est valable que si le compte est actif et que sa version n'a pas
 * changé depuis l'émission : désactivation, réinitialisation, changement de profil
 * ou révocation par le super administrateur ferment aussitôt les sessions.
 */
class SessionValideValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESSION_REVOQUEE = new OAuth2Error("invalid_token", "Session fermée : reconnectez-vous.", null);

    private final UtilisateurRepository utilisateurs;

    SessionValideValidator(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jeton) {
        Object version = jeton.getClaims().get(JetonService.VERSION);
        boolean valide = utilisateurs.findById(jeton.getSubject())
                .filter(u -> u.isActif() && version instanceof Number n && n.intValue() == u.getVersionJeton())
                .isPresent();
        return valide ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(SESSION_REVOQUEE);
    }
}
