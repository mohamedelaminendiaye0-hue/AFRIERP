package sn.afrierp.notarius.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.UtilisateurRepository;

@Component
public class UtilisateurCourantResolver implements HandlerMethodArgumentResolver {

    public static final String EN_TETE = "X-Utilisateur";
    private static final String PAR_DEFAUT = "U-001";

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
        String id = webRequest.getHeader(EN_TETE);
        Utilisateur u = utilisateurs.findById(id == null || id.isBlank() ? PAR_DEFAUT : id)
                .orElseThrow(() -> new RegleMetierException("UTILISATEUR_INCONNU", "Utilisateur inconnu : " + id));
        if (!u.isActif()) {
            throw new RegleMetierException("UTILISATEUR_INACTIF", "Ce compte utilisateur est desactive.");
        }
        return u;
    }
}
