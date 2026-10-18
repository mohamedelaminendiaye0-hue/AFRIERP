package sn.afrierp.notarius.workflow;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.audit.AuditService;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.domain.Bien;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.Intervenant;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.TypeActe;
import sn.afrierp.notarius.domain.TypeEvenement;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;

/** Ouverture d'un dossier : statut DRAFT, checklist standard du type d'acte. */
@Service
public class DossierService {

    private final DossierRepository dossiers;
    private final AuditService audit;
    private final Clock clock;

    public DossierService(DossierRepository dossiers, AuditService audit, Clock clock) {
        this.dossiers = dossiers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Dossier creer(String intitule, TypeActe typeActe, long montantTransaction, Utilisateur clerc,
                         Utilisateur notaire, Bien bien, List<Intervenant> intervenants, Utilisateur auteur) {
        if (!EnumSet.of(Role.ASSISTANT, Role.CLERC, Role.NOTAIRE, Role.DIRECTEUR).contains(auteur.getRole())) {
            throw new RegleMetierException("ROLE_NON_HABILITE", "Ouverture de dossier reservee a l'accueil, aux clercs et aux notaires.");
        }
        if (clerc.getRole() != Role.CLERC || !clerc.isActif()) {
            throw new RegleMetierException("CLERC_INVALIDE", "Le clerc redacteur doit etre un clerc actif.");
        }
        if (notaire.getRole() != Role.NOTAIRE && notaire.getRole() != Role.DIRECTEUR) {
            throw new RegleMetierException("NOTAIRE_INVALIDE", "Le superviseur doit etre un notaire.");
        }
        LocalDate date = LocalDate.now(clock);
        long sequence = dossiers.count() + 147;
        String id = "D-%04d".formatted(sequence);
        while (dossiers.existsById(id)) {
            id = "D-%04d".formatted(++sequence);
        }

        Dossier d = new Dossier();
        d.setId(id);
        d.setReference("%d-%s-%04d".formatted(date.getYear(), typeActe.getCode(), sequence));
        d.setIntitule(intitule.trim());
        d.setTypeActe(typeActe);
        d.setStatut(StatutDossier.DRAFT);
        d.setDateEntreeStatut(date);
        d.setDateOuverture(date);
        d.setMontantTransaction(montantTransaction);
        d.setClerc(clerc);
        d.setNotaire(notaire);
        d.setBien(bien);
        intervenants.forEach(i -> {
            i.setKycValide(false);
            d.ajouterIntervenant(i);
        });
        ModelesPieces.pour(typeActe).forEach(m -> d.ajouterPiece(m.instancier()));
        Dossier enregistre = dossiers.save(d);
        audit.tracer(enregistre, TypeEvenement.CREATION, auteur, "Ouverture du dossier — " + enregistre.getIntitule(),
                Map.of("reference", enregistre.getReference(), "clerc", clerc.getId(), "notaire", notaire.getId()));
        return enregistre;
    }
}
