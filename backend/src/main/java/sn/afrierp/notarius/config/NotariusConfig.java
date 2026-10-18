package sn.afrierp.notarius.config;

import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import sn.afrierp.notarius.web.UtilisateurCourantResolver;

@Configuration
public class NotariusConfig implements WebMvcConfigurer {

    private final UtilisateurCourantResolver utilisateurCourantResolver;

    public NotariusConfig(UtilisateurCourantResolver utilisateurCourantResolver) {
        this.utilisateurCourantResolver = utilisateurCourantResolver;
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
}
