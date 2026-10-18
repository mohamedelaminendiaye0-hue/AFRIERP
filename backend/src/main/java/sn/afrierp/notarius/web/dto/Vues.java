package sn.afrierp.notarius.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import sn.afrierp.notarius.bareme.DecompteTaxation;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.compta.ComptaRecords.LigneReleve;
import sn.afrierp.notarius.compta.SousCompte;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.domain.Bien;
import sn.afrierp.notarius.domain.DomaineActe;
import sn.afrierp.notarius.domain.EvenementAudit;
import sn.afrierp.notarius.domain.Intervenant;
import sn.afrierp.notarius.domain.ModeSignature;
import sn.afrierp.notarius.domain.Formalite;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.RendezVous;
import sn.afrierp.notarius.domain.Salle;
import sn.afrierp.notarius.domain.StatutRendezVous;
import sn.afrierp.notarius.domain.TypeRendezVous;
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
                                 String roleLibelle, String initiales, boolean actif, boolean notaire,
                                 boolean encadrement) {

        public static UtilisateurVue de(Utilisateur u) {
            return new UtilisateurVue(u.getId(), u.getPrenom(), u.getNom(), u.nomComplet(), u.getRole(),
                    u.getRole().getLibelle(), u.getInitiales(), u.isActif(), u.getRole().estNotaire(),
                    Role.ENCADREMENT.contains(u.getRole()));
        }
    }

    /** Fiche d'un membre de l'etude (vue « Equipe »). */
    public record MembreVue(UtilisateurVue utilisateur, String description, String referenceNomination,
                            String notaireResponsable, LocalDate dateEntree, long dossiersRediges,
                            long dossiersSupervises, long rendezVousAVenir) {
    }

    public record RendezVousVue(Long id, TypeRendezVous type, String typeLibelle, StatutRendezVous statut,
                                String statutLibelle, LocalDateTime debut, LocalDateTime fin, int dureeMinutes,
                                Salle salle, String salleLibelle, String notaireId, String notaireNom,
                                String collaborateurId, String collaborateurNom, String dossierId,
                                String dossierReference, String clientNom, String clientTelephone, String objet,
                                int nombreReports, String motifAnnulation) {

        public static RendezVousVue de(RendezVous r) {
            return new RendezVousVue(r.getId(), r.getType(), r.getType().getLibelle(), r.getStatut(),
                    r.getStatut().getLibelle(), r.getDebut(), r.getFin(), r.getDureeMinutes(), r.getSalle(),
                    r.getSalle().getLibelle(), r.getNotaire() == null ? null : r.getNotaire().getId(),
                    r.getNotaire() == null ? null : r.getNotaire().nomComplet(),
                    r.getCollaborateur() == null ? null : r.getCollaborateur().getId(),
                    r.getCollaborateur() == null ? null : r.getCollaborateur().nomComplet(),
                    r.getDossier() == null ? null : r.getDossier().getId(),
                    r.getDossier() == null ? null : r.getDossier().getReference(), r.getClientNom(),
                    r.getClientTelephone(), r.getObjet(), r.getNombreReports(), r.getMotifAnnulation());
        }
    }

    public record FormaliteVue(OrganismeFormalites organisme, String libelle, String objet, boolean requise,
                               LocalDate dateDepot, String reference, LocalDate dateAchevement) {

        public static FormaliteVue de(OrganismeFormalites o, boolean requise, Formalite f) {
            return new FormaliteVue(o, o.getLibelle(), o.getObjet(), requise, f == null ? null : f.getDateDepot(),
                    f == null ? null : f.getReference(), f == null ? null : f.getDateAchevement());
        }
    }

    public record MinuteVue(String numeroRepertoire, LocalDate dateArchivage, String archiveParNom,
                            LocalDate conservationJusquau) {
    }

    /** Ligne du minutier (repertoire des minutes archivees). */
    public record LigneMinutier(String numeroRepertoire, String dossierId, String reference, String intitule,
                                String typeActeLibelle, LocalDate dateSignature, String notaireSignataire,
                                String notaireResponsable, LocalDate dateArchivage, LocalDate conservationJusquau) {
    }

    public record StatutVue(StatutDossier code, String libelle, String acteur, String description, int rang,
                            Integer delaiCibleJours) {
    }

    public record TransitionVue(StatutDossier de, StatutDossier vers, String action, List<Role> roles,
                                List<String> rolesLibelles, boolean notaireDuDossier, boolean retour) {

        public static TransitionVue de(Transition t) {
            List<Role> roles = t.roles().stream().sorted().toList();
            return new TransitionVue(t.de(), t.vers(), t.action(), roles, roles.stream().map(Role::getLibelle).toList(),
                    t.notaireDuDossier(), t.retour());
        }
    }

    public record WorkflowVue(List<StatutVue> statuts, List<TransitionVue> transitions) {
    }

    /** Fiche d'un type d'acte du catalogue (parametres fiscaux et proceduraux). */
    public record TypeActeVue(TypeActe code, String libelle, String codeReference, DomaineActe domaine,
                              String domaineLibelle, boolean complexe, String assietteLibelle, String enregistrement,
                              boolean publiciteFonciere, String emolument, Bareme.Sequestre sequestre,
                              List<String> formalites, Integer delaiOppositionJours, boolean plusValue) {

        public static TypeActeVue de(TypeActe t) {
            Bareme.ParametresActe p = Bareme.parametres(t);
            String enr = p.tauxEnregistrement() != null
                    ? String.format(java.util.Locale.FRANCE, "Droit proportionnel %.2f %%", p.tauxEnregistrement() * 100)
                    : String.format(java.util.Locale.FRANCE, "Droit fixe %,d FCFA", p.droitFixe());
            String emo = p.emolumentFixe() == null ? "Bareme proportionnel degressif"
                    : String.format(java.util.Locale.FRANCE, "Forfait %,d FCFA", p.emolumentFixe());
            return new TypeActeVue(t, t.getLibelle(), t.getCode(), t.getDomaine(), t.getDomaine().getLibelle(),
                    t.estComplexe(), t.getAssietteLibelle(), enr, p.publiciteFonciere(), emo, p.sequestre(),
                    p.formalites().stream().map(OrganismeFormalites::getLibelle).toList(), p.delaiOppositionJours(),
                    p.plusValue());
        }
    }

    public record DossierLie(String id, String reference, String intitule) {
    }

    /** Verdict d'une transition sortante pour l'utilisateur courant. */
    public record ActionVue(TransitionVue transition, boolean autorisee, List<Blocage> blocages) {
    }

    public record DossierResume(
            String id, String reference, String intitule, TypeActe typeActe, String typeActeLibelle,
            StatutDossier statut, String statutLibelle, String clercId, String clercNom, String notaireId,
            String notaireNom, boolean typeComplexe, DomaineActe domaine, LocalDate dateOuverture, LocalDate dateSignaturePrevue, LocalDate dateSignature,
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

    public record Signature(ModeSignature mode, String modeLibelle, LocalDate date, String signataireNom,
                            String signataireRole, String responsableNom, StatutGainde gaindeStatut,
                            String gaindeStatutLibelle, LocalDate gaindeDateMiseAJour, String gaindeReference) {
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
            DossierLie dossierLie,
            String assietteLibelle,
            Bareme.Sequestre sequestre,
            /* Cession de fonds de commerce : date a partir de laquelle le prix peut etre remis au vendeur. */
            LocalDate finDelaiOpposition,
            Visa visaPrincipalClerc,
            Visa taxageDefinitif,
            Long fraisDefinitifs,
            Signature signature,
            List<FormaliteVue> formalites,
            MinuteVue minute,
            List<RendezVousVue> rendezVous,
            SousCompte sousCompte,
            List<LigneReleve> releve,
            List<ActionVue> actions,
            List<EvenementVue> journal) {
    }
}
