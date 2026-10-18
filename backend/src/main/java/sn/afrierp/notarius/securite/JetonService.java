package sn.afrierp.notarius.securite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import sn.afrierp.notarius.admin.PolitiqueSecurite;
import sn.afrierp.notarius.domain.Utilisateur;

/**
 * Émet le jeton de session (JWT signé HS256) remis au navigateur après connexion.
 * Revendications : {@code role} (droits), {@code ver} (version du compte, pour
 * révoquer les sessions) et {@code chg} (mot de passe provisoire à changer).
 */
@Service
public class JetonService {

    public static final String VERSION = "ver";
    public static final String A_CHANGER = "chg";

    private final JwtEncoder encodeur;
    private final PolitiqueSecurite politique;
    private final Clock clock;

    public JetonService(JwtEncoder encodeur, PolitiqueSecurite politique, Clock clock) {
        this.encodeur = encodeur;
        this.politique = politique;
        this.clock = clock;
    }

    public record Jeton(String valeur, Instant expiration) {
    }

    public Jeton emettre(Utilisateur u) {
        Instant maintenant = Instant.now(clock);
        Instant expiration = maintenant.plus(Duration.ofHours(politique.parametres().getDureeSessionHeures()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("notarius")
                .subject(u.getId())
                .issuedAt(maintenant)
                .expiresAt(expiration)
                .claim("role", u.getRole().name())
                .claim(VERSION, u.getVersionJeton())
                .claim(A_CHANGER, u.isDoitChangerMotDePasse())
                .build();
        String valeur = encodeur.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Jeton(valeur, expiration);
    }
}
