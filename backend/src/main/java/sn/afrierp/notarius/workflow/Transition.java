package sn.afrierp.notarius.workflow;

import java.util.List;
import java.util.Set;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.StatutDossier;

/**
 * Transition autorisee du cycle de vie.
 *
 * @param notaireDuDossier reservee au notaire superviseur du dossier (ou au directeur)
 * @param retour           retour arriere : motif obligatoire, trace au journal
 */
public record Transition(StatutDossier de, StatutDossier vers, String action, Set<Role> roles,
                         boolean notaireDuDossier, boolean retour) {

    private static Transition avant(StatutDossier de, StatutDossier vers, String action, Role... roles) {
        return new Transition(de, vers, action, Set.of(roles), false, false);
    }

    /** W1 — seules ces transitions existent : aucune etape ne peut etre sautee. */
    public static final List<Transition> TOUTES = List.of(
            avant(StatutDossier.DRAFT, StatutDossier.PENDING_DOCS, "Ouvrir l'instruction", Role.ASSISTANT, Role.CLERC, Role.DIRECTEUR),
            avant(StatutDossier.PENDING_DOCS, StatutDossier.DRAFTING, "Lancer la redaction", Role.CLERC, Role.DIRECTEUR),
            new Transition(StatutDossier.DRAFTING, StatutDossier.PENDING_DOCS, "Revenir a l'instruction", Set.of(Role.CLERC, Role.DIRECTEUR), false, true),
            avant(StatutDossier.DRAFTING, StatutDossier.REVIEW, "Soumettre au notaire", Role.CLERC, Role.DIRECTEUR),
            new Transition(StatutDossier.REVIEW, StatutDossier.DRAFTING, "Renvoyer pour correction", Set.of(Role.NOTAIRE, Role.DIRECTEUR), true, true),
            new Transition(StatutDossier.REVIEW, StatutDossier.TAXING, "Valider le projet d'acte", Set.of(Role.NOTAIRE, Role.DIRECTEUR), true, false),
            avant(StatutDossier.TAXING, StatutDossier.FUNDING, "Arreter le taxage previsionnel", Role.COMPTABLE, Role.DIRECTEUR),
            new Transition(StatutDossier.FUNDING, StatutDossier.TAXING, "Reprendre le taxage", Set.of(Role.COMPTABLE, Role.DIRECTEUR), false, true),
            avant(StatutDossier.FUNDING, StatutDossier.READY_TO_SIGN, "Certifier les fonds consignes", Role.COMPTABLE, Role.DIRECTEUR),
            new Transition(StatutDossier.READY_TO_SIGN, StatutDossier.SIGNED, "Signer la minute", Set.of(Role.NOTAIRE, Role.DIRECTEUR), true, false),
            avant(StatutDossier.SIGNED, StatutDossier.POST_ACT, "Engager les formalites", Role.FORMALISTE, Role.DIRECTEUR),
            avant(StatutDossier.POST_ACT, StatutDossier.CLOSED, "Cloturer et archiver", Role.COMPTABLE, Role.DIRECTEUR));
}
