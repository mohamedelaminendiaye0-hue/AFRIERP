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
import sn.afrierp.notarius.workflow.DossierService;
import sn.afrierp.notarius.workflow.ModelesPieces;
import sn.afrierp.notarius.workflow.RendezVousService;
import sn.afrierp.notarius.workflow.WorkflowService;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.Salle;
import sn.afrierp.notarius.domain.SensEcriture;
import sn.afrierp.notarius.domain.StatutPiece;
import sn.afrierp.notarius.domain.StatutRendezVous;
import sn.afrierp.notarius.domain.TypeRendezVous;

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
    @Autowired RendezVousService rendezVous;
    @Autowired DossierService dossierService;

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
        assertThat(obligatoires).extracting(f -> f.sousCompte().dossierId()).contains("D-0091", "D-0087")
                .doesNotContain("D-0082"); // reliquat restitué : plus de fonds immobilisés
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
        // Notaire salarie qui ne supervise pas ce dossier
        assertThat(codes(workflow.verifier(d, StatutDossier.TAXING, u("U-010"), null, null))).containsExactly("NOTAIRE_NON_SUPERVISEUR");
        // Notaire titulaire (superviseur) et notaire associe de la SCP : notaires en titre
        assertThat(workflow.verifier(d, StatutDossier.TAXING, u("U-001"), null, null).autorisee()).isTrue();
        assertThat(workflow.verifier(d, StatutDossier.TAXING, u("U-002"), null, null).autorisee()).isTrue();
    }

    @Test
    void W3_redaction_bloquee_si_pieces_manquantes_ou_etat_des_droits_reels_perime() {
        Dossier d = dossiers.findById("D-0131").orElseThrow();
        VerdictTransition v = workflow.verifier(d, StatutDossier.DRAFTING, u("U-005"), null, null);
        assertThat(codes(v)).contains("PIECES_MANQUANTES", "PIECES_PERIMEES");
        assertThat(v.blocages().stream().filter(b -> b.code().equals("PIECES_PERIMEES")).findFirst().orElseThrow().message())
                .contains("État des droits réels");
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
        assertThat(codes(workflow.verifier(d, StatutDossier.CLOSED, u("U-007"), null, null))).contains("ROLE_NON_HABILITE");
        assertThat(codes(workflow.verifier(d, StatutDossier.CLOSED, u("U-012"), null, null)))
                .containsExactly("FORMALITES_INCOMPLETES", "TAXAGE_DEFINITIF_ABSENT", "RELIQUAT_NON_RESTITUE");
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
        // Ouvert par l'accueil sans clerc : le principal clerc l'affecte.
        assertThat(codes(workflow.verifier(workflow.dossier(id), StatutDossier.PENDING_DOCS, u("U-009"), null, null)))
                .containsExactly("CLERC_NON_ASSIGNE");
        workflow.assigner(id, u("U-004"), null, u("U-011"));
        workflow.appliquer(id, StatutDossier.PENDING_DOCS, u("U-009"), null, null);

        Dossier d = workflow.dossier(id);
        for (PieceJustificative p : d.getPieces()) {
            workflow.majPiece(id, p.getId(), StatutPiece.VALIDEE, HorlogeDeTest.AUJOURDHUI, null, u("U-004"));
        }
        workflow.appliquer(id, StatutDossier.DRAFTING, u("U-004"), null, null);
        workflow.viserProjet(id, u("U-011"));
        workflow.appliquer(id, StatutDossier.REVIEW, u("U-004"), null, null);
        workflow.appliquer(id, StatutDossier.TAXING, u("U-002"), null, null);
        workflow.appliquer(id, StatutDossier.FUNDING, u("U-007"), null, null);

        long besoin = workflow.couverture(workflow.dossier(id)).besoin();
        compta.ecrire(workflow.dossier(id), SensEcriture.CREDIT, besoin + 500_000,
                NatureMouvement.PRIX_VENTE, "Sequestre prix + frais", "Acquereur", u("U-007"));
        compta.compteCollectif().setSoldeBancaire(compta.compteCollectif().getSoldeBancaire() + besoin + 500_000);
        workflow.validerKyc(id, workflow.dossier(id).getIntervenants().get(0).getId(), u("U-004"));
        workflow.appliquer(id, StatutDossier.READY_TO_SIGN, u("U-007"), null, null);

        assertThat(codes(workflow.verifier(workflow.dossier(id), StatutDossier.SIGNED, u("U-002"), null, ModeSignature.PHYSIQUE)))
                .containsExactly("RDV_NON_PLANIFIE");
        rendezVous.creer(signature(workflow.dossier(id), JEUDI.atTime(14, 0), "U-002"), u("U-009"));
        assertThat(workflow.dossier(id).getDateSignaturePrevue()).isEqualTo(JEUDI);
        workflow.appliquer(id, StatutDossier.SIGNED, u("U-002"), null, ModeSignature.ELECTRONIQUE_GAINDE);
        assertThat(rendezVous.signatureActive(id)).isEmpty(); // le rendez-vous est honore
        workflow.majStatutGainde(id, StatutGainde.VALIDE, null, u("U-002"));
        workflow.appliquer(id, StatutDossier.POST_ACT, u("U-008"), null, null);
        workflow.deposerFormalite(id, OrganismeFormalites.CONSERVATION_FONCIERE, "CF-1", u("U-008"));
        workflow.acheverFormalite(id, OrganismeFormalites.CONSERVATION_FONCIERE, u("U-008"));
        assertThat(codes(workflow.verifier(workflow.dossier(id), StatutDossier.CLOSED, u("U-012"), null, null)))
                .contains("FORMALITES_INCOMPLETES"); // la mutation fiscale (DGID) reste a faire
        workflow.deposerFormalite(id, OrganismeFormalites.DGID, "ENR-1", u("U-008"));
        workflow.acheverFormalite(id, OrganismeFormalites.DGID, u("U-008"));
        workflow.arreterTaxageDefinitif(id, u("U-007"));

        compta.executerVirement(workflow.dossier(id), compta.solde(id), NatureMouvement.RESTITUTION_CLIENT, "Acquereur", u("U-007"));
        Dossier cloture = workflow.appliquer(id, StatutDossier.CLOSED, u("U-012"), null, null);

        assertThat(cloture.getStatut()).isEqualTo(StatutDossier.CLOSED);
        assertThat(cloture.getNumeroRepertoire()).isEqualTo("2026/0010"); // inscrit au repertoire des la signature
        assertThat(cloture.getConservationJusquau()).isEqualTo(HorlogeDeTest.AUJOURDHUI.plusYears(75));
        assertThat(cloture.getNotaireResponsableId()).isEqualTo("U-002");
        assertThat(compta.controlerEtancheite().conforme()).isTrue();
        assertThat(evenements.findByDossierIdOrderByHorodatageDescIdDesc(id).stream()
                .filter(e -> e.getType() == TypeEvenement.CHANGEMENT_STATUT).count()).isEqualTo(9);
    }

    /* ------------------------------------------------------------------ *
     *  Acteurs de l'etude
     * ------------------------------------------------------------------ */

    @Test
    void dossier_complexe_soumis_au_notaire_apres_visa_du_principal_clerc() {
        Dossier d = dossiers.findById("D-0138").orElseThrow(); // vente, redaction en cours
        assertThat(codes(workflow.verifier(d, StatutDossier.REVIEW, u("U-004"), null, null))).containsExactly("VISA_PRINCIPAL_CLERC_ABSENT");
        assertThatThrownBy(() -> workflow.viserProjet("D-0138", u("U-004"))).isInstanceOf(RegleMetierException.class);
        workflow.viserProjet("D-0138", u("U-011"));
        assertThat(workflow.verifier(d, StatutDossier.REVIEW, u("U-004"), null, null).autorisee()).isTrue();
        // Un dossier simple (donation) n'exige pas ce visa.
        Dossier donation = dossiers.findById("D-0104").orElseThrow();
        assertThat(workflow.verifier(donation, StatutDossier.REVIEW, u("U-005"), null, null).autorisee()).isTrue();
    }

    @Test
    void assignation_reservee_a_lencadrement() {
        assertThatThrownBy(() -> workflow.assigner("D-0138", u("U-003"), null, u("U-004")))
                .isInstanceOf(RegleMetierException.class);
        // Le principal clerc affecte les clercs, pas les notaires.
        workflow.assigner("D-0138", u("U-003"), null, u("U-011"));
        assertThat(workflow.dossier("D-0138").getClerc().getId()).isEqualTo("U-003");
        assertThatThrownBy(() -> workflow.assigner("D-0138", null, u("U-001"), u("U-011")))
                .isInstanceOf(RegleMetierException.class);
        workflow.assigner("D-0138", null, u("U-010"), u("U-002"));
        assertThat(workflow.dossier("D-0138").getNotaire().getId()).isEqualTo("U-010");
        assertThat(evenements.findByDossierIdOrderByHorodatageDescIdDesc("D-0138"))
                .filteredOn(e -> e.getType() == TypeEvenement.ASSIGNATION).hasSize(2);
    }

    @Test
    void notaire_salarie_signe_sous_la_responsabilite_du_titulaire() {
        workflow.assigner("D-0112", null, u("U-010"), u("U-001"));
        Dossier signe = workflow.appliquer("D-0112", StatutDossier.SIGNED, u("U-010"), null, ModeSignature.PHYSIQUE);
        assertThat(signe.getNotaireSignataireId()).isEqualTo("U-010");
        assertThat(signe.getNotaireResponsableId()).isEqualTo("U-001");
    }

    /* ------------------------------------------------------------------ *
     *  Prise de rendez-vous
     * ------------------------------------------------------------------ */

    private static final java.time.LocalDate JEUDI = HorlogeDeTest.AUJOURDHUI.plusDays(1);

    private RendezVousService.Demande signature(Dossier d, java.time.LocalDateTime debut, String notaire) {
        return new RendezVousService.Demande(TypeRendezVous.SIGNATURE, debut, 90, Salle.SALLE_SIGNATURES, u(notaire), null, d,
                null, null, "Signature");
    }

    private RendezVousService.Demande consultation(java.time.LocalDateTime debut, String notaire, Salle salle) {
        return new RendezVousService.Demande(TypeRendezVous.CONSULTATION, debut, 45, salle, u(notaire), null, null,
                "M. Test", null, "Consultation");
    }

    private List<String> codes(List<Blocage> blocages) {
        return blocages.stream().map(Blocage::code).toList();
    }

    @Test
    void rendez_vous_refuse_en_cas_de_conflit_ou_hors_horaires() {
        // Seed : Ousmane Sarr recoit les heritiers Camara jeudi 09:30-10:30.
        assertThat(codes(rendezVous.verifier(consultation(JEUDI.atTime(10, 0), "U-002", Salle.BUREAU_NOTAIRE), null)))
                .containsExactly("CONFLIT_NOTAIRE");
        assertThat(rendezVous.verifier(consultation(JEUDI.atTime(10, 30), "U-002", Salle.BUREAU_NOTAIRE), null)).isEmpty();
        // Seed : signature D-0112 vendredi 10:00 en salle des signatures (salle partagee).
        assertThat(codes(rendezVous.verifier(consultation(JEUDI.plusDays(1).atTime(10, 15), "U-001", Salle.SALLE_SIGNATURES), null)))
                .containsExactly("CONFLIT_SALLE");
        assertThat(codes(rendezVous.verifier(consultation(JEUDI.plusDays(2).atTime(10, 0), "U-001", Salle.BUREAU_NOTAIRE), null)))
                .containsExactly("HORS_JOURS_OUVRES");
        assertThat(codes(rendezVous.verifier(consultation(JEUDI.atTime(17, 30), "U-001", Salle.BUREAU_NOTAIRE), null)))
                .containsExactly("HORS_HORAIRES");
        assertThat(codes(rendezVous.verifier(consultation(HorlogeDeTest.AUJOURDHUI.atTime(8, 0), "U-001", Salle.BUREAU_NOTAIRE), null)))
                .containsExactly("DATE_PASSEE");
    }

    @Test
    void rendez_vous_de_signature_lie_au_cycle_du_dossier() {
        Dossier vedette = dossiers.findById("D-0142").orElseThrow(); // en attente de provision
        assertThat(codes(rendezVous.verifier(signature(vedette, JEUDI.atTime(11, 0), "U-001"), null))).containsExactly("DOSSIER_NON_PRET");
        Dossier d112 = dossiers.findById("D-0112").orElseThrow();
        assertThat(codes(rendezVous.verifier(signature(d112, JEUDI.atTime(14, 0), "U-002"), null))).containsExactly("SIGNATURE_DEJA_PLANIFIEE");

        var rdv = rendezVous.signatureActive("D-0112").orElseThrow();
        rendezVous.reporter(rdv.getId(), JEUDI.plusDays(5).atTime(11, 0), null, u("U-009"));
        assertThat(d112.getDateSignaturePrevue()).isEqualTo(JEUDI.plusDays(5));
        assertThatThrownBy(() -> rendezVous.changerStatut(rdv.getId(), StatutRendezVous.ANNULE, " ", u("U-009")))
                .isInstanceOf(RegleMetierException.class);
        rendezVous.changerStatut(rdv.getId(), StatutRendezVous.ANNULE, "Banque non prete", u("U-009"));
        assertThat(d112.getDateSignaturePrevue()).isNull();
        assertThat(codes(workflow.verifier(d112, StatutDossier.SIGNED, u("U-002"), null, ModeSignature.PHYSIQUE)))
                .containsExactly("RDV_NON_PLANIFIE");
    }

    /* ------------------------------------------------------------------ *
     *  Moteur de taxation
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------ *
     *  Catalogue des actes et regles propres a certaines demarches
     * ------------------------------------------------------------------ */

    @Test
    void chaque_type_dacte_a_ses_parametres_et_sa_checklist() {
        for (TypeActe t : TypeActe.values()) {
            Bareme.ParametresActe p = Bareme.parametres(t);
            assertThat(p).as("parametres %s", t).isNotNull();
            assertThat(ModelesPieces.pour(t)).as("checklist %s", t).isNotEmpty();
            DecompteTaxation d = taxation.simuler(t, t.aUneAssiette() ? 50_000_000 : 0);
            assertThat(d.lignes().stream().mapToLong(DecompteTaxation.LigneDecompte::montant).sum())
                    .as("total %s", t).isEqualTo(d.totaux().totalGeneral());
            assertThat(d.lignes().stream().anyMatch(l -> l.code().equals("CF"))).as("publicite fonciere %s", t)
                    .isEqualTo(p.publiciteFonciere());
            if (!t.aUneAssiette()) {
                assertThat(p.tauxEnregistrement()).as("droit fixe %s", t).isNull();
                assertThat(p.emolumentFixe()).as("emolument fixe %s", t).isNotNull();
            }
        }
    }

    @Test
    void acte_sans_assiette_procuration_taxe_au_forfait_et_ouvrable_sans_prix() {
        DecompteTaxation d = taxation.simuler(TypeActe.PROCURATION, 0);
        assertThat(d.lignes()).extracting(DecompteTaxation.LigneDecompte::code).contains("ENR", "EMO-FIXE").doesNotContain("CF", "EMO-PROP");
        Dossier p = dossierService.creer("Procuration — test", TypeActe.PROCURATION, 0, null, u("U-004"), u("U-001"), null,
                List.of(new sn.afrierp.notarius.domain.Intervenant(sn.afrierp.notarius.domain.TypePersonne.PHYSIQUE, "M. Test",
                        sn.afrierp.notarius.domain.QualiteIntervenant.MANDATAIRE, null, false)), null, u("U-011"));
        assertThat(workflow.verifier(p, StatutDossier.PENDING_DOCS, u("U-009"), null, null).autorisee()).isTrue();
    }

    @Test
    void fonds_de_commerce_prix_sequestre_pendant_le_delai_dopposition() {
        Dossier d = dossiers.findById("D-0150").orElseThrow();
        var verdict = compta.autoriserVirement(d, 1_000_000, NatureMouvement.VERSEMENT_VENDEUR, u("U-007"));
        assertThat(verdict.code()).isEqualTo("DELAI_OPPOSITION_EN_COURS");
        assertThat(verdict.message()).contains(HorlogeDeTest.AUJOURDHUI.minusDays(12).plusDays(30)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        // Les paiements au Tresor restent possibles.
        assertThat(compta.autoriserVirement(d, 100_000, NatureMouvement.PAIEMENT_TRESOR, u("U-007")).autorise()).isTrue();
    }

    @Test
    void capital_libere_seulement_apres_immatriculation_au_rccm() {
        Dossier constitution = dossiers.findById("D-0127").orElseThrow();
        assertThat(compta.autoriserVirement(constitution, 1_000_000, NatureMouvement.DEBLOCAGE_CAPITAL, u("U-007")).code())
                .isEqualTo("IMMATRICULATION_REQUISE");
        Dossier vente = dossiers.findById("D-0138").orElseThrow();
        assertThat(compta.autoriserVirement(vente, 1_000_000, NatureMouvement.DEBLOCAGE_CAPITAL, u("U-007")).code())
                .isEqualTo("NATURE_INVALIDE");
    }

    @Test
    void sequestre_du_capital_exige_avant_la_signature_dune_constitution() {
        Dossier constitution = dossiers.findById("D-0127").orElseThrow();
        assertThat(workflow.couverture(constitution).prix()).isEqualTo(constitution.getMontantTransaction());
        Dossier hypotheque = dossiers.findById("D-0112").orElseThrow();
        assertThat(workflow.couverture(hypotheque).prix()).isZero(); // le pret n'est pas sequestre
    }

    @Test
    void mainlevee_rattachee_a_une_affectation_hypothecaire() {
        assertThatThrownBy(() -> dossierService.creer("Mainlevee", TypeActe.MAINLEVEE_HYPOTHEQUE, 10_000_000, null, null,
                u("U-001"), null, List.of(), "D-0138", u("U-009")))
                .isInstanceOf(RegleMetierException.class);
        Dossier m = dossierService.creer("Mainlevee — Sy", TypeActe.MAINLEVEE_HYPOTHEQUE, 52_000_000, null, null,
                u("U-001"), null, List.of(), "D-0112", u("U-009"));
        assertThat(m.getDossierLieId()).isEqualTo("D-0112");
        assertThat(m.getReference()).contains("-MH-");
    }

    @Test
    void taxe_sur_la_plus_value_retenue_sur_le_prix_hors_total_a_provisionner() {
        DecompteTaxation sans = taxation.simuler(TypeActe.VENTE_IMMOBILIERE, 185_000_000);
        DecompteTaxation avec = taxation.simuler(TypeActe.VENTE_IMMOBILIERE, 185_000_000, 0, 120_000_000L);
        assertThat(avec.totaux().retenueVendeur()).isEqualTo(Math.round(65_000_000 * Bareme.TAUX_PLUS_VALUE));
        assertThat(avec.totaux().totalGeneral()).isEqualTo(sans.totaux().totalGeneral());
    }

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
