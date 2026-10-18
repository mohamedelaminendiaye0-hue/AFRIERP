package sn.afrierp.notarius.web;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.UtilisateurRepository;

/** Fournit aux controleurs l'utilisateur authentifie, identifie par le sujet de son jeton. */
@Component
public class UtilisateurCourantResolver implements HandlerMethodArgumentResolver {

    private final UtilisateurRepository utilisateurs;

    public UtilisateurCourantResolver(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UtilisateurCourant.class)
                && Utilisateur.class.equals(parameter.getParameterType());
    }

    @Override
    public Utilisateur resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RegleMetierException("NON_AUTHENTIFIE", "Session absente ou expirée : reconnectez-vous.");
        }
        Utilisateur u = utilisateurs.findById(auth.getName())
                .orElseThrow(() -> new RegleMetierException("UTILISATEUR_INCONNU", "Utilisateur inconnu : " + auth.getName()));
        if (!u.isActif()) {
            throw new RegleMetierException("UTILISATEUR_INACTIF", "Ce compte utilisateur est désactivé.");
        }
        return u;
    }
}
