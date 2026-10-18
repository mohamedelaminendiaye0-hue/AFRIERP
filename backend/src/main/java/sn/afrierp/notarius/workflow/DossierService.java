package sn.afrierp.notarius.workflow;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.audit.AuditService;
import sn.afrierp.notarius.clients.ClientService;
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
    private final ClientService clients;
    private final Clock clock;

    public DossierService(DossierRepository dossiers, AuditService audit, ClientService clients, Clock clock) {
        this.dossiers = dossiers;
        this.clients = clients;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Dossier creer(String intitule, TypeActe typeActe, long montantTransaction, Long prixAcquisition,
                         Utilisateur clerc, Utilisateur notaire, Bien bien, List<Intervenant> intervenants,
                         String dossierLieId, Utilisateur auteur) {
        if (!EnumSet.of(Role.ASSISTANT, Role.CLERC, Role.PRINCIPAL_CLERC, Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE,
                Role.NOTAIRE_SALARIE).contains(auteur.getRole())) {
            throw new RegleMetierException("ROLE_NON_HABILITE", "Ouverture de dossier réservée à l'accueil, aux clercs et aux notaires.");
        }
        // Le clerc peut etre affecte plus tard par le principal clerc (file « a assigner »).
        if (clerc != null && ((clerc.getRole() != Role.CLERC && clerc.getRole() != Role.PRINCIPAL_CLERC) || !clerc.isActif())) {
            throw new RegleMetierException("CLERC_INVALIDE", "Le clerc rédacteur doit être un clerc actif.");
        }
        if (!notaire.getRole().estNotaire() || !notaire.isActif()) {
            throw new RegleMetierException("NOTAIRE_INVALIDE", "Le superviseur doit être un notaire actif.");
        }
        if (clerc != null && !Role.ENCADREMENT.contains(auteur.getRole()) && !clerc.getId().equals(auteur.getId())) {
            throw new RegleMetierException("ROLE_NON_HABILITE",
                    "Seuls le principal clerc et les notaires en titre affectent un dossier à un autre clerc.");
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
        d.setPrixAcquisition(prixAcquisition);
        if (dossierLieId != null && !dossierLieId.isBlank()) {
            Dossier lie = dossiers.findById(dossierLieId)
                    .orElseThrow(() -> new RegleMetierException("DOSSIER_LIE_INCONNU", "Dossier d'origine introuvable : " + dossierLieId));
            if (typeActe == TypeActe.MAINLEVEE_HYPOTHEQUE && lie.getTypeActe() != TypeActe.AFFECTATION_HYPOTHECAIRE) {
                throw new RegleMetierException("DOSSIER_LIE_INVALIDE", "Une mainlevée se rattache à une affectation hypothécaire.");
            }
            d.setDossierLieId(lie.getId());
        }
        d.setClerc(clerc);
        d.setNotaire(notaire);
        d.setBien(bien);
        intervenants.forEach(i -> {
            i.setKycValide(false);
            d.ajouterIntervenant(i);
            clients.rattacher(i);
        });
        ModelesPieces.pour(typeActe).forEach(m -> d.ajouterPiece(m.instancier()));
        Dossier enregistre = dossiers.save(d);
        audit.tracer(enregistre, TypeEvenement.CREATION, auteur, "Ouverture du dossier — " + enregistre.getIntitule(),
                Map.of("reference", enregistre.getReference(), "clerc", clerc == null ? "à assigner" : clerc.getId(),
                        "notaire", notaire.getId()));
        return enregistre;
    }
}
