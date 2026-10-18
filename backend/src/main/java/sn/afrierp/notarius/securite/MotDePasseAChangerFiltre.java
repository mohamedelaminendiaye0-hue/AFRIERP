package sn.afrierp.notarius.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tant que le mot de passe provisoire n'est pas remplacé, la session ne donne
 * accès qu'à l'écran de changement de mot de passe (et à l'identité de l'étude).
 */
class MotDePasseAChangerFiltre extends OncePerRequestFilter {

    private static final String CORPS = "{\"message\":\"Choisissez votre mot de passe personnel avant de continuer.\","
            + "\"blocages\":[{\"code\":\"MOT_DE_PASSE_A_CHANGER\",\"message\":\"Mot de passe provisoire à remplacer.\"}]}";

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String chemin = requete.getRequestURI();
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth
                && Boolean.TRUE.equals(auth.getToken().getClaim(JetonService.A_CHANGER))
                && chemin.startsWith("/api/") && !chemin.startsWith("/api/auth/") && !chemin.equals("/api/identite")
                && !chemin.equals("/api/moi")) {
            reponse.setStatus(HttpStatus.FORBIDDEN.value());
            reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            reponse.getWriter().write(CORPS);
            return;
        }
        chaine.doFilter(requete, reponse);
    }
}
