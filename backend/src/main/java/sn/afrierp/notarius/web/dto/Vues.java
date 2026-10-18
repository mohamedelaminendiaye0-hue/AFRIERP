package sn.afrierp.notarius.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import sn.afrierp.notarius.bareme.DecompteTaxation;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.compta.ComptaRecords.LigneReleve;
import sn.afrierp.notarius.compta.SousCompte;
import sn.afrierp.notarius.domain.Bien;
import sn.afrierp.notarius.domain.EvenementAudit;
import sn.afrierp.notarius.domain.Intervenant;
import sn.afrierp.notarius.domain.ModeSignature;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.StatutGainde;
import sn.afrierp.notarius.domain.TypeActe;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.workflow.Transition;
import sn.afrierp.notarius.workflow.WorkflowRecords.CouvertureFonds;

/** Representations JSON exposees par l'API. */
public final class Vues {

    private Vues() {
    }

    public record UtilisateurVue(String id, String prenom, String nom, String nomComplet, Role role,
                                 String roleLibelle, String initiales, boolean actif) {

        public static UtilisateurVue de(Utilisateur u) {
            return new UtilisateurVue(u.getId(), u.getPrenom(), u.getNom(), u.nomComplet(), u.getRole(),
                    u.getRole().getLibelle(), u.getInitiales(), u.isActif());
        }
    }

    public record StatutVue(StatutDossier code, String libelle, String acteur, String description, int rang,
                            Integer delaiCibleJours) {
    }

    public record TransitionVue(StatutDossier de, StatutDossier vers, String action, List<Role> roles,
                                boolean notaireDuDossier, boolean retour) {

        public static TransitionVue de(Transition t) {
            return new TransitionVue(t.de(), t.vers(), t.action(), t.roles().stream().sorted().toList(),
                    t.notaireDuDossier(), t.retour());
        }
    }

    public record WorkflowVue(List<StatutVue> statuts, List<TransitionVue> transitions) {
    }

    /** Verdict d'une transition sortante pour l'utilisateur courant. */
    public record ActionVue(TransitionVue transition, boolean autorisee, List<Blocage> blocages) {
    }

    public record DossierResume(
            String id, String reference, String intitule, TypeActe typeActe, String typeActeLibelle,
            StatutDossier statut, String statutLibelle, String clercId, String clercNom, String notaireId,
            String notaireNom, LocalDate dateOuverture, LocalDate dateSignaturePrevue, LocalDate dateSignature,
            long montantTransaction, double tauxCompletudePieces, long soldeFonds, long joursDansStatut,
            Integer delaiCibleJours, long retardStatutJours, long retardSignatureJours, boolean alerteCdc,
            boolean purgeConditionsEnCours, ModeSignature modeSignature, StatutGainde gaindeStatut) {
    }

    public record PieceVue(Long id, int ordre, String libelle, String categorie, boolean obligatoire,
                           StatutDossier exigibleA, String exigibleALibelle, Integer validiteJours,
                           String responsable, String statut, LocalDate dateDemande, LocalDate dateReception,
                           LocalDate dateEmission, LocalDate dateExpiration, boolean perimee, String commentaire) {
    }

    public record EvenementVue(Long id, LocalDateTime horodatage, String type, String auteurId, String auteurNom,
                               StatutDossier statutDossier, String statutLibelle, String libelle,
                               Map<String, String> detail) {

        public static EvenementVue de(EvenementAudit e, String auteurNom) {
            return new EvenementVue(e.getId(), e.getHorodatage(), e.getType().name(), e.getAuteurId(), auteurNom,
                    e.getStatutDossier(), e.getStatutDossier().getLibelle(), e.getLibelle(), e.getDetail());
        }
    }

    public record IntervenantVue(Long id, String type, String nom, String qualite, String identifiant,
                                 String telephone, String email, boolean kycValide) {

        public static IntervenantVue de(Intervenant i) {
            return new IntervenantVue(i.getId(), i.getType().name(), i.getNom(), i.getQualite().name(),
                    i.getIdentifiant(), i.getTelephone(), i.getEmail(), i.isKycValide());
        }
    }

    public record Visa(String parId, String parNom, LocalDate date) {
    }

    public record Signature(ModeSignature mode, String modeLibelle, LocalDate date, StatutGainde gaindeStatut,
                            String gaindeStatutLibelle, LocalDate gaindeDateMiseAJour, String gaindeReference) {
    }

    public record Formalites(OrganismeFormalites organisme, LocalDate dateDepot, String reference) {
    }

    public record DossierDetail(
            DossierResume resume,
            Bien bien,
            List<IntervenantVue> intervenants,
            List<PieceVue> pieces,
            DecompteTaxation taxation,
            Long fraisPrevisionnels,
            String versionBareme,
            CouvertureFonds couverture,
            Visa validationNotaire,
            Visa certificationFonds,
            Long montantCertifie,
            Signature signature,
            Formalites formalites,
            SousCompte sousCompte,
            List<LigneReleve> releve,
            List<ActionVue> actions,
            List<EvenementVue> journal) {
    }
}
