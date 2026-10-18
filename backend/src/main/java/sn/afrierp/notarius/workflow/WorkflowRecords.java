package sn.afrierp.notarius.workflow;

import java.util.List;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.domain.NiveauAlerte;
import sn.afrierp.notarius.domain.PieceJustificative;
import sn.afrierp.notarius.domain.StatutDossier;

public final class WorkflowRecords {

    private WorkflowRecords() {
    }

    /** Passerelle comptable : fonds encaisses sur le sequestre face au besoin (frais + prix). */
    public record CouvertureFonds(long frais, long prix, long besoin, long encaisse, long manquant, boolean couvert) {
    }

    public record VerdictTransition(boolean autorisee, Transition transition, List<Blocage> blocages,
                                    CouvertureFonds couverture) {
    }

    public record ControlePieces(List<PieceJustificative> manquantes, List<PieceJustificative> perimees) {
    }

    public record Delais(long joursDansStatut, Integer delaiCibleJours, long retardJours) {
    }

    public record NotificationRetard(String dossierId, String reference, String intitule, StatutDossier statut,
                                     long joursDansStatut, int delaiCibleJours, long retardJours,
                                     String clercId, String notaireId, NiveauAlerte niveau) {
    }
}
