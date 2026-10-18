package sn.afrierp.notarius;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.bareme.DecompteTaxation;
import sn.afrierp.notarius.bareme.TaxationService;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.compta.ComptaRecords.ControleEtancheite;
import sn.afrierp.notarius.compta.ComptaService;
import sn.afrierp.notarius.compta.SousCompte;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.ModeSignature;
import sn.afrierp.notarius.domain.NatureMouvement;
import sn.afrierp.notarius.domain.PieceJustificative;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.StatutGainde;
import sn.afrierp.notarius.domain.TypeActe;
import sn.afrierp.notarius.domain.TypeEvenement;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.EvenementAuditRepository;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.workflow.WorkflowRecords.NotificationRetard;
import sn.afrierp.notarius.workflow.WorkflowRecords.VerdictTransition;
import sn.afrierp.notarius.workflow.WorkflowService;

/**
 * Controles de coherence du jeu de donnees et des regles metier : invariants
 * comptables (I1-I3), cycle de vie (W1-W5) et moteur de taxation.
 */
@SpringBootTest
@Import(HorlogeDeTest.class)
@Transactional
class ReglesMetierTest {

    @Autowired WorkflowService workflow;
    @Autowired ComptaService compta;
    @Autowired TaxationService taxation;
    @Autowired DossierRepository dossiers;
    @Autowired UtilisateurRepository utilisateurs;
    @Autowired EvenementAuditRepository evenements;

    private Utilisateur u(String id) {
        return utilisateurs.findById(id).orElseThrow();
    }

    private static List<String> codes(VerdictTransition v) {
        return v.blocages().stream().map(Blocage::code).toList();
    }

    /* ------------------------------------------------------------------ *
     *  Invariants comptables
     * ------------------------------------------------------------------ */

    @Test
    void I2_somme_des_sous_comptes_egale_solde_bancaire_et_I1_aucun_debiteur() {
        ControleEtancheite controle = compta.controlerEtancheite();
        assertThat(controle.ecart()).isZero();
        assertThat(controle.sousComptesDebiteurs()).isEmpty();
        assertThat(controle.conforme()).isTrue();
    }

    @Test
    void releve_solde_final_egal_solde_du_sous_compte() {
        for (SousCompte sc : compta.sousComptes()) {
            var releve = compta.releve(sc.dossierId());
            assertThat(releve.get(releve.size() - 1).soldeCumule()).isEqualTo(sc.solde());
        }
    }

    @Test
    void I3_regle_cdc_detecte_les_fonds_immobilises_au_dela_du_seuil() {
        var obligatoires = compta.detecterFondsImmobilises().stream().filter(f -> f.transfertObligatoire()).toList();
        assertThat(obligatoires).extracting(f -> f.sousCompte().dossierId()).contains("D-0091", "D-0087", "D-0082");
        assertThat(obligatoires).allMatch(f -> f.ancienneteJours() >= Bareme.CDC_SEUIL_IMMOBILISATION_JOURS);
    }

    @Test
    void virement_refuse_des_que_le_montant_depasse_le_solde() {
        Dossier d = dossiers.findById("D-0138").orElseThrow();
        long solde = compta.solde("D-0138");
        assertThat(compta.autoriserVirement(d, solde + 1, NatureMouvement.PAIEMENT_TIERS, u("U-007")).code()).isEqualTo("SOLDE_INSUFFISANT");
        assertThat(compta.autoriserVirement(d, solde, NatureMouvement.PAIEMENT_TIERS, u("U-007")).autorise()).isTrue();
        assertThat(compta.autoriserVirement(d, 1000, NatureMouvement.PAIEMENT_TIERS, u("U-003")).code()).isEqualTo("HABILITATION_MANQUANTE");
        assertThat(compta.autoriserVirement(d, 1000, NatureMouvement.VERSEMENT_VENDEUR, u("U-007")).code()).isEqualTo("DOSSIER_NON_SIGNE");
    }

    @Test
    void virement_refuse_est_trace_au_journal_et_aucune_ecriture_passee() {
        Dossier d = dossiers.findById("D-0138").orElseThrow();
        long solde = compta.solde("D-0138");
        assertThatThrownBy(() -> compta.executerVirement(d, solde + 1, NatureMouvement.PAIEMENT_TIERS, "X", u("U-007")))
                .isInstanceOf(RegleMetierException.class);
        assertThat(compta.solde("D-0138")).isEqualTo(solde);
        assertThat(evenements.findByDossierIdOrderByHorodatageDescIdDesc("D-0138"))
                .anyMatch(e -> e.getType() == TypeEvenement.TENTATIVE_BLOQUEE);
    }

    /* ------------------------------------------------------------------ *
     *  Coherence du portefeuille avec le workflow
     * ------------------------------------------------------------------ */

    @Test
    void W3_aucun_dossier_na_franchi_une_etape_avec_une_piece_exigible_manquante() {
        for (Dossier d : dossiers.findAll()) {
            List<PieceJustificative> manquantes = workflow.controlerPieces(d, d.getStatut()).manquantes();
            assertThat(manquantes).as("pieces manquantes du dossier %s", d.getId()).isEmpty();
        }
    }

    @Test
    void W4_tout_dossier_certifie_couvrait_son_besoin_de_financement() {
        for (Dossier d : dossiers.findAll()) {
            if (d.getStatut().estAuMoins(StatutDossier.READY_TO_SIGN) && compta.sousCompte(d.getId()).isPresent()) {
                assertThat(d.getMontantCertifie()).as("certification %s", d.getId()).isNotNull();
                assertThat(d.getMontantCertifie()).as("couverture %s", d.getId()).isGreaterThanOrEqualTo(workflow.couverture(d).besoin());
            }
        }
    }

    /* ------------------------------------------------------------------ *
     *  Transitions
     * ------------------------------------------------------------------ */

    @Test
    void W1_aucune_etape_ne_peut_etre_sautee() {
        Dossier d = dossiers.findById("D-0146").orElseThrow();
        assertThat(codes(workflow.verifier(d, StatutDossier.DRAFTING, u("U-001"), null, null))).containsExactly("TRANSITION_INTERDITE");
        assertThat(codes(workflow.verifier(d, StatutDossier.SIGNED, u("U-001"), null, null))).containsExactly("TRANSITION_INTERDITE");
    }

    @Test
    void W2_transitions_reservees_aux_roles_et_au_notaire_du_dossier() {
        Dossier d = dossiers.findById("D-0127").orElseThrow(); // REVIEW, notaire U-001
        assertThat(codes(workflow.verifier(d, StatutDossier.TAXING, u("U-006"), null, null))).containsExactly("ROLE_NON_HABILITE");
        assertThat(codes(workflow.verifier(d, StatutDossier.TAXING, u("U-002"), null, null))).containsExactly("NOTAIRE_NON_TITULAIRE");
        assertThat(workflow.verifier(d, StatutDossier.TAXING, u("U-001"), null, null).autorisee()).isTrue();
    }

    @Test
    void W3_redaction_bloquee_si_pieces_manquantes_ou_etat_des_droits_reels_perime() {
        Dossier d = dossiers.findById("D-0131").orElseThrow();
        VerdictTransition v = workflow.verifier(d, StatutDossier.DRAFTING, u("U-005"), null, null);
        assertThat(codes(v)).contains("PIECES_MANQUANTES", "PIECES_PERIMEES");
        assertThat(v.blocages().stream().filter(b -> b.code().equals("PIECES_PERIMEES")).findFirst().orElseThrow().message())
                .contains("Etat des droits reels");
    }

    @Test
    void W4_mise_a_la_signature_conditionnee_par_la_couverture_des_fonds() {
        Dossier vedette = dossiers.findById("D-0142").orElseThrow();
        VerdictTransition v = workflow.verifier(vedette, StatutDossier.READY_TO_SIGN, u("U-007"), null, null);
        assertThat(codes(v)).contains("FONDS_INSUFFISANTS", "PIECES_MANQUANTES");
        assertThat(v.couverture().prix()).isEqualTo(185_000_000);
        assertThat(v.couverture().manquant()).isEqualTo(v.couverture().besoin() - v.couverture().encaisse()).isPositive();
    }

    @Test
    void retour_arriere_exige_un_motif() {
        Dossier d = dossiers.findById("D-0127").orElseThrow();
        assertThat(codes(workflow.verifier(d, StatutDossier.DRAFTING, u("U-001"), " ", null))).containsExactly("MOTIF_OBLIGATOIRE");
        assertThat(workflow.verifier(d, StatutDossier.DRAFTING, u("U-001"), "Clause de garantie a revoir", null).autorisee()).isTrue();
    }

    @Test
    void transition_appliquee_trace_le_statut_de_depart_et_pose_le_visa() {
        Dossier d = workflow.appliquer("D-0127", StatutDossier.TAXING, u("U-001"), null, null);
        assertThat(d.getStatut()).isEqualTo(StatutDossier.TAXING);
        assertThat(d.getValidationNotaireParId()).isEqualTo("U-001");
        assertThat(d.getDateEntreeStatut()).isEqualTo(HorlogeDeTest.AUJOURDHUI);
        var changement = evenements.findByDossierIdOrderByHorodatageDescIdDesc("D-0127").stream()
                .filter(e -> e.getType() == TypeEvenement.CHANGEMENT_STATUT).findFirst().orElseThrow();
        assertThat(changement.getStatutDossier()).isEqualTo(StatutDossier.REVIEW);
        assertThat(changement.getDetail()).containsEntry("avant", "REVIEW").containsEntry("apres", "TAXING");
    }

    @Test
    void transition_refusee_leve_une_erreur_et_trace_la_tentative() {
        assertThatThrownBy(() -> workflow.appliquer("D-0142", StatutDossier.READY_TO_SIGN, u("U-007"), null, null))
                .isInstanceOf(RegleMetierException.class)
                .satisfies(e -> assertThat(((RegleMetierException) e).getBlocages()).extracting(Blocage::code).contains("FONDS_INSUFFISANTS"));
        assertThat(dossiers.findById("D-0142").orElseThrow().getStatut()).isEqualTo(StatutDossier.FUNDING);
        assertThat(evenements.findByDossierIdOrderByHorodatageDescIdDesc("D-0142").get(0).getType()).isEqualTo(TypeEvenement.TENTATIVE_BLOQUEE);
    }

    @Test
    void gainde_les_formalites_attendent_la_validation_de_la_signature_electronique() {
        Dossier d = dossiers.findById("D-0125").orElseThrow();
        assertThat(codes(workflow.verifier(d, StatutDossier.POST_ACT, u("U-008"), null, null))).containsExactly("GAINDE_NON_VALIDE");
        workflow.majStatutGainde("D-0125", StatutGainde.VALIDE, "GAINDE-2026-120001", u("U-002"));
        assertThat(workflow.verifier(d, StatutDossier.POST_ACT, u("U-008"), null, null).autorisee()).isTrue();
    }

    @Test
    void W5_cloture_bloquee_tant_que_le_reliquat_nest_pas_restitue() {
        Dossier d = dossiers.findById("D-0116").orElseThrow();
        assertThat(codes(workflow.verifier(d, StatutDossier.CLOSED, u("U-007"), null, null))).containsExactly("RELIQUAT_NON_RESTITUE");
    }

    @Test
    void notifications_de_retard_adressees_au_clerc_et_au_notaire() {
        List<NotificationRetard> notifications = workflow.notificationsRetard(dossiers.findAll());
        NotificationRetard d131 = notifications.stream().filter(n -> n.dossierId().equals("D-0131")).findFirst().orElseThrow();
        assertThat(d131.retardJours()).isEqualTo(90 - Bareme.DELAIS_CIBLES_JOURS.get(StatutDossier.PENDING_DOCS));
        assertThat(d131.clercId()).isEqualTo("U-005");
        assertThat(workflow.notificationsPour(u("U-005"))).allMatch(n -> n.clercId().equals("U-005") || n.notaireId().equals("U-005"));
    }

    /* ------------------------------------------------------------------ *
     *  Parcours complet : de la creation a l'archivage
     * ------------------------------------------------------------------ */

    @Test
    void parcours_complet_dun_dossier_de_vente() {
        String id = "D-0146";
        workflow.appliquer(id, StatutDossier.PENDING_DOCS, u("U-009"), null, null);

        Dossier d = workflow.dossier(id);
        for (PieceJustificative p : d.getPieces()) {
            workflow.majPiece(id, p.getId(), sn.afrierp.notarius.domain.StatutPiece.VALIDEE, HorlogeDeTest.AUJOURDHUI, null, u("U-004"));
        }
        workflow.appliquer(id, StatutDossier.DRAFTING, u("U-004"), null, null);
        workflow.appliquer(id, StatutDossier.REVIEW, u("U-004"), null, null);
        workflow.appliquer(id, StatutDossier.TAXING, u("U-002"), null, null);
        workflow.appliquer(id, StatutDossier.FUNDING, u("U-007"), null, null);

        long besoin = workflow.couverture(workflow.dossier(id)).besoin();
        compta.ecrire(workflow.dossier(id), sn.afrierp.notarius.domain.SensEcriture.CREDIT, besoin + 500_000,
                NatureMouvement.PRIX_VENTE, "Sequestre prix + frais", "Acquereur", u("U-007"));
        compta.compteCollectif().setSoldeBancaire(compta.compteCollectif().getSoldeBancaire() + besoin + 500_000);
        workflow.validerKyc(id, workflow.dossier(id).getIntervenants().get(0).getId(), u("U-004"));
        workflow.appliquer(id, StatutDossier.READY_TO_SIGN, u("U-007"), null, null);

        workflow.planifierSignature(id, HorlogeDeTest.AUJOURDHUI.plusDays(3), u("U-009"));
        workflow.appliquer(id, StatutDossier.SIGNED, u("U-002"), null, ModeSignature.ELECTRONIQUE_GAINDE);
        workflow.majStatutGainde(id, StatutGainde.VALIDE, null, u("U-002"));
        workflow.appliquer(id, StatutDossier.POST_ACT, u("U-008"), null, null);
        workflow.deposerFormalites(id, sn.afrierp.notarius.domain.OrganismeFormalites.CONSERVATION_FONCIERE, "CF-1", u("U-008"));

        compta.executerVirement(workflow.dossier(id), compta.solde(id), NatureMouvement.RESTITUTION_CLIENT, "Acquereur", u("U-007"));
        Dossier cloture = workflow.appliquer(id, StatutDossier.CLOSED, u("U-007"), null, null);

        assertThat(cloture.getStatut()).isEqualTo(StatutDossier.CLOSED);
        assertThat(cloture.getDateCloture()).isEqualTo(HorlogeDeTest.AUJOURDHUI);
        assertThat(compta.controlerEtancheite().conforme()).isTrue();
        assertThat(evenements.findByDossierIdOrderByHorodatageDescIdDesc(id).stream()
                .filter(e -> e.getType() == TypeEvenement.CHANGEMENT_STATUT).count()).isEqualTo(9);
    }

    /* ------------------------------------------------------------------ *
     *  Moteur de taxation
     * ------------------------------------------------------------------ */

    @Test
    void taxation_total_egal_somme_des_lignes_et_tva_sur_emoluments_seuls() {
        DecompteTaxation tax = taxation.simuler(TypeActe.VENTE_IMMOBILIERE, 185_000_000);
        assertThat(tax.lignes().stream().mapToLong(DecompteTaxation.LigneDecompte::montant).sum()).isEqualTo(tax.totaux().totalGeneral());
        long assietteTva = tax.totaux().emoluments() + tax.totaux().honoraires();
        assertThat(tax.totaux().tva()).isEqualTo(Math.round(assietteTva * Bareme.TVA_TAUX));
        assertThat(tax.lignes().get(0).montant()).isEqualTo(Math.round(185_000_000 * 0.05));
        assertThat((double) taxation.emolumentsProportionnels(185_000_000) / 185_000_000).isLessThan(0.03);
        assertThat(taxation.emolumentsProportionnels(500_000)).isEqualTo(Bareme.EMOLUMENTS_MINIMUM);
    }
}
