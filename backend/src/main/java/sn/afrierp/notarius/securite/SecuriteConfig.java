package sn.afrierp.notarius.securite;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API sans etat : chaque requete porte un jeton « Authorization: Bearer ». Seules la
 * connexion et l'identite de l'etude (necessaire a la page de connexion) sont publiques.
 */
@Configuration
public class SecuriteConfig {

    private final SecretKey cle;
    private final String[] originesAutorisees;
    private final Clock clock;

    public SecuriteConfig(@Value("${notarius.jwt.secret}") String secret,
                          @Value("${notarius.cors.origines:http://localhost:4200}") String[] originesAutorisees,
                          Clock clock) {
        this.clock = clock;
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < 32) {
            throw new IllegalStateException("notarius.jwt.secret doit contenir au moins 32 caractères.");
        }
        this.cle = new SecretKeySpec(octets, "HmacSHA256");
        this.originesAutorisees = originesAutorisees;
    }

    @Bean
    public SecurityFilterChain filtres(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
                .cors(c -> c.configurationSource(cors()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/connexion").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/comptes-demo", "/api/identite").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(jwtDecoder())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(cle).macAlgorithm(MacAlgorithm.HS256).build();
        // Expiration jugee sur l'horloge de l'application (figee en test).
        JwtTimestampValidator expiration = new JwtTimestampValidator();
        expiration.setClock(clock);
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(expiration, new JwtIssuerValidator("notarius")));
        return decodeur;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(cle));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource cors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(originesAutorisees));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
