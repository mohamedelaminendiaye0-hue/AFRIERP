package sn.afrierp.notarius.audit;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.EvenementAudit;
import sn.afrierp.notarius.domain.TypeEvenement;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.EvenementAuditRepository;

/** Ecriture du journal d'audit (append-only) : seule porte d'entree vers la table. */
@Service
public class AuditService {

    private final EvenementAuditRepository repository;
    private final Clock clock;

    public AuditService(EvenementAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public EvenementAudit tracer(Dossier dossier, TypeEvenement type, Utilisateur auteur, String libelle,
                                 Map<String, String> detail) {
        return repository.save(new EvenementAudit(dossier.getId(), LocalDateTime.now(clock), type, auteur.getId(),
                dossier.getStatut(), libelle, detail));
    }

    /**
     * Trace une tentative refusee dans une transaction independante : le refus
     * annule l'operation, mais la trace de la tentative doit, elle, subsister.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tracerTentativeBloquee(Dossier dossier, Utilisateur auteur, String libelle, Map<String, String> detail) {
        tracer(dossier, TypeEvenement.TENTATIVE_BLOQUEE, auteur, libelle, detail);
    }

    public List<EvenementAudit> journal(String dossierId) {
        return repository.findByDossierIdOrderByHorodatageDescIdDesc(dossierId);
    }
}
