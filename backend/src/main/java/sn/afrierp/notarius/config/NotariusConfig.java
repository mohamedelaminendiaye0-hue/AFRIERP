package sn.afrierp.notarius.config;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import sn.afrierp.notarius.web.UtilisateurCourantResolver;

@Configuration
public class NotariusConfig implements WebMvcConfigurer {

    private final UtilisateurCourantResolver utilisateurCourantResolver;
    private final String[] originesAutorisees;

    public NotariusConfig(UtilisateurCourantResolver utilisateurCourantResolver,
                          @Value("${notarius.cors.origines:http://localhost:4200}") String[] originesAutorisees) {
        this.utilisateurCourantResolver = utilisateurCourantResolver;
        this.originesAutorisees = originesAutorisees;
    }

    /** Horloge injectable : les tests figent la date, la production suit l'horloge systeme. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(utilisateurCourantResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(originesAutorisees)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
    }
}
