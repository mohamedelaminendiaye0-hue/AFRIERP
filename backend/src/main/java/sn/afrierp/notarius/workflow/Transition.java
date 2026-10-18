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

    private static final Role[] REDACTION = {Role.CLERC, Role.PRINCIPAL_CLERC, Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE};
    private static final Role[] NOTAIRES = {Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE, Role.NOTAIRE_SALARIE};
    private static final Role[] TAXATION = {Role.COMPTABLE_TAXATEUR, Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE};

    private static Role[] avec(Role[] base, Role... autres) {
        Role[] r = java.util.Arrays.copyOf(base, base.length + autres.length);
        System.arraycopy(autres, 0, r, base.length, autres.length);
        return r;
    }

    /**
     * W1 — seules ces transitions existent : aucune etape ne peut etre sautee.
     * W2 — chaque transition est reservee a des profils ; la validation et la
     * signature reviennent au notaire superviseur du dossier (ou a un notaire en titre).
     */
    public static final List<Transition> TOUTES = List.of(
            avant(StatutDossier.DRAFT, StatutDossier.PENDING_DOCS, "Ouvrir l'instruction", avec(REDACTION, Role.ASSISTANT)),
            avant(StatutDossier.PENDING_DOCS, StatutDossier.DRAFTING, "Lancer la redaction", REDACTION),
            new Transition(StatutDossier.DRAFTING, StatutDossier.PENDING_DOCS, "Revenir a l'instruction", Set.of(REDACTION), false, true),
            avant(StatutDossier.DRAFTING, StatutDossier.REVIEW, "Soumettre au notaire", REDACTION),
            new Transition(StatutDossier.REVIEW, StatutDossier.DRAFTING, "Renvoyer pour correction", Set.of(NOTAIRES), true, true),
            new Transition(StatutDossier.REVIEW, StatutDossier.TAXING, "Valider le projet d'acte", Set.of(NOTAIRES), true, false),
            avant(StatutDossier.TAXING, StatutDossier.FUNDING, "Arreter le taxage previsionnel", TAXATION),
            new Transition(StatutDossier.FUNDING, StatutDossier.TAXING, "Reprendre le taxage", Set.of(TAXATION), false, true),
            avant(StatutDossier.FUNDING, StatutDossier.READY_TO_SIGN, "Certifier les fonds consignes", TAXATION),
            new Transition(StatutDossier.READY_TO_SIGN, StatutDossier.SIGNED, "Signer la minute", Set.of(NOTAIRES), true, false),
            avant(StatutDossier.SIGNED, StatutDossier.POST_ACT, "Engager les formalites", Role.FORMALISTE, Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE),
            avant(StatutDossier.POST_ACT, StatutDossier.CLOSED, "Archiver la minute et cloturer", Role.ARCHIVISTE, Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE));
}
