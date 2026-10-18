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
import sn.afrierp.notarius.domain.Utilisateur;

/** Emet le jeton de session (JWT signe HS256) remis au navigateur apres connexion. */
@Service
public class JetonService {

    public static final Duration DUREE = Duration.ofHours(10);

    private final JwtEncoder encodeur;
    private final Clock clock;

    public JetonService(JwtEncoder encodeur, Clock clock) {
        this.encodeur = encodeur;
        this.clock = clock;
    }

    public record Jeton(String valeur, Instant expiration) {
    }

    public Jeton emettre(Utilisateur u) {
        Instant maintenant = Instant.now(clock);
        Instant expiration = maintenant.plus(DUREE);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("notarius")
                .subject(u.getId())
                .issuedAt(maintenant)
                .expiresAt(expiration)
                .claim("role", u.getRole().name())
                .build();
        String valeur = encodeur.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Jeton(valeur, expiration);
    }
}
