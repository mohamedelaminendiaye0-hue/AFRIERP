package sn.afrierp.notarius.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sn.afrierp.notarius.domain.EvenementAdmin;
import sn.afrierp.notarius.domain.TypeEvenementAdmin;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.EvenementAdminRepository;

/**
 * Journal d'administration : connexions, gestion des comptes et réglages de la
 * plateforme. Chaque trace est écrite dans sa propre transaction, pour qu'un
 * échec de connexion ou une action refusée reste consigné.
 */
@Service
public class JournalAdmin {

    private final EvenementAdminRepository depot;
    private final Clock clock;

    public JournalAdmin(EvenementAdminRepository depot, Clock clock) {
        this.depot = depot;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tracer(TypeEvenementAdmin type, Utilisateur acteur, Utilisateur cible, String detail) {
        tracer(type, acteur, cible == null ? null : cible.getId(), cible == null ? null : cible.nomComplet(), detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tracer(TypeEvenementAdmin type, Utilisateur acteur, String cibleId, String cibleNom, String detail) {
        depot.save(new EvenementAdmin(LocalDateTime.now(clock), type, acteur == null ? null : acteur.getId(),
                acteur == null ? null : acteur.nomComplet(), cibleId, cibleNom, detail, adresseIp()));
    }

    private static String adresseIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs)) {
            return null;
        }
        HttpServletRequest requete = attributs.getRequest();
        String relais = requete.getHeader("X-Forwarded-For");
        return relais != null && !relais.isBlank() ? relais.split(",")[0].trim() : requete.getRemoteAddr();
    }
}
