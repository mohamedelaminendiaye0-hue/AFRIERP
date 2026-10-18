package sn.afrierp.notarius.compta;

import java.time.LocalDate;
import java.util.List;
import sn.afrierp.notarius.domain.MouvementComptable;
import sn.afrierp.notarius.domain.NiveauAlerte;

/** Resultats des controles du maniement de fonds. */
public final class ComptaRecords {

    private ComptaRecords() {
    }

    /** Invariants I1 (aucun sous-compte debiteur) et I2 (somme des auxiliaires = solde bancaire). */
    public record ControleEtancheite(long soldeBancaire, long sommeAuxiliaires, long ecart, boolean conforme,
                                     List<SousCompte> sousComptesDebiteurs, LocalDate dateControle) {
    }

    public record VerdictVirement(boolean autorise, String code, String message, long soldeDisponible,
                                  long montantRequis, long manquant) {
    }

    public record FondsImmobilise(SousCompte sousCompte, long ancienneteJours, long montant,
                                  boolean transfertObligatoire) {
    }

    public record AlerteConformite(String id, String dossierId, String reference, String intitule,
                                   NiveauAlerte niveau, String regle, String message, long montantConcerne,
                                   Long ancienneteJours, String actionSuggeree, LocalDate dateDetection) {
    }

    public record LigneReleve(Long id, String numeroPiece, LocalDate dateOperation, String sens, long montant,
                              String nature, String libelle, String contrepartie, String moyenPaiement,
                              boolean rapproche, long soldeCumule) {

        static LigneReleve de(MouvementComptable m, long soldeCumule) {
            return new LigneReleve(m.getId(), m.getNumeroPiece(), m.getDateOperation(), m.getSens().name(),
                    m.getMontant(), m.getNature().name(), m.getLibelle(), m.getContrepartie(),
                    m.getMoyenPaiement().name(), m.isRapproche(), soldeCumule);
        }
    }

    public record OrdreVirementCdc(String reference, LocalDate dateEmission, String beneficiaire, String objet,
                                   long montant, String dossierReference, long ancienneteJours,
                                   String fondementReglementaire, String mentionConformite) {
    }
}
