package sn.afrierp.notarius.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.bareme.TaxationService;
import sn.afrierp.notarius.compta.ComptaService;
import sn.afrierp.notarius.domain.Bien;
import sn.afrierp.notarius.domain.CompteCollectif;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.EvenementAudit;
import sn.afrierp.notarius.domain.Intervenant;
import sn.afrierp.notarius.domain.ModeSignature;
import sn.afrierp.notarius.domain.MoyenPaiement;
import sn.afrierp.notarius.domain.MouvementComptable;
import sn.afrierp.notarius.domain.NatureMouvement;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.PieceJustificative;
import sn.afrierp.notarius.domain.QualiteIntervenant;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.SensEcriture;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.StatutGainde;
import sn.afrierp.notarius.domain.StatutPiece;
import sn.afrierp.notarius.domain.TypeActe;
import sn.afrierp.notarius.domain.TypeEvenement;
import sn.afrierp.notarius.domain.TypePersonne;
import sn.afrierp.notarius.domain.UsageBien;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.CompteCollectifRepository;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.EvenementAuditRepository;
import sn.afrierp.notarius.repository.MouvementComptableRepository;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.workflow.ModelesPieces;

/**
 * JEU DE DONNEES DE DEMONSTRATION — etude notariale a Dakar. Donnees fictives,
 * montants en XOF, dates relatives au jour de demarrage.
 *
 * <p>Rien n'est saisi deux fois : les soldes des sous-comptes et le solde du
 * compte collectif decoulent des ecritures, le rapprochement est donc juste
 * par construction (I2). Charge uniquement si la base est vide.
 */
@Component
@ConditionalOnProperty(name = "notarius.donnees-demo", havingValue = "true", matchIfMissing = true)
public class DonneesDemo implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DonneesDemo.class);
    public static final String DOSSIER_VEDETTE = "D-0142";

    private final UtilisateurRepository utilisateurs;
    private final DossierRepository dossiers;
    private final MouvementComptableRepository mouvements;
    private final EvenementAuditRepository evenements;
    private final CompteCollectifRepository comptes;
    private final TaxationService taxation;
    private final Clock clock;

    private LocalDate aujourdhui;
    private final Map<String, Utilisateur> users = new HashMap<>();

    public DonneesDemo(UtilisateurRepository utilisateurs, DossierRepository dossiers,
                       MouvementComptableRepository mouvements, EvenementAuditRepository evenements,
                       CompteCollectifRepository comptes, TaxationService taxation, Clock clock) {
        this.utilisateurs = utilisateurs;
        this.dossiers = dossiers;
        this.mouvements = mouvements;
        this.evenements = evenements;
        this.comptes = comptes;
        this.taxation = taxation;
        this.clock = clock;
    }

    private LocalDate ilYA(int jours) {
        return aujourdhui.minusDays(jours);
    }

    private LocalDate dans(int jours) {
        return aujourdhui.plusDays(jours);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (utilisateurs.count() > 0) {
            return;
        }
        aujourdhui = LocalDate.now(clock);
        chargerUtilisateurs();
        chargerDossiers();
        chargerEcritures();
        poserVisas();
        chargerJournalVedette();
        LOG.info("Donnees de demonstration chargees : {} dossiers, {} ecritures.", dossiers.count(), mouvements.count());
    }

    /* ------------------------------------------------------------------ *
     *  Utilisateurs
     * ------------------------------------------------------------------ */

    private void chargerUtilisateurs() {
        List.of(
                new Utilisateur("U-001", "Aminata", "Ndoye", Role.DIRECTEUR, "AN", LocalDate.of(2009, 3, 2)),
                new Utilisateur("U-002", "Ousmane", "Sarr", Role.NOTAIRE, "OS", LocalDate.of(2014, 9, 1)),
                new Utilisateur("U-003", "Awa", "Diallo", Role.CLERC, "AD", LocalDate.of(2017, 1, 16)),
                new Utilisateur("U-004", "Ibrahima", "Sow", Role.CLERC, "IS", LocalDate.of(2019, 5, 6)),
                new Utilisateur("U-005", "Coumba", "Ndiaye", Role.CLERC, "CN", LocalDate.of(2021, 2, 15)),
                new Utilisateur("U-006", "Moussa", "Faye", Role.CLERC, "MF", LocalDate.of(2022, 10, 10)),
                new Utilisateur("U-007", "Cheikh", "Diop", Role.COMPTABLE, "CD", LocalDate.of(2016, 4, 20)),
                new Utilisateur("U-008", "Mariama", "Tall", Role.FORMALISTE, "MT", LocalDate.of(2019, 6, 3)),
                new Utilisateur("U-009", "Ndeye", "Ba", Role.ASSISTANT, "NB", LocalDate.of(2023, 1, 9))
        ).forEach(u -> users.put(u.getId(), utilisateurs.save(u)));
    }

    /* ------------------------------------------------------------------ *
     *  Dossiers
     * ------------------------------------------------------------------ */

    private Dossier dossier(String id, String reference, String intitule, TypeActe type, StatutDossier statut,
                            int depuis, String clercId, String notaireId, int ouvertJours, Integer signaturePrevue,
                            long montant, int nbPiecesRecues) {
        Dossier d = new Dossier();
        d.setId(id);
        d.setReference(reference);
        d.setIntitule(intitule);
        d.setTypeActe(type);
        d.setStatut(statut);
        d.setDateEntreeStatut(ilYA(depuis));
        d.setClerc(users.get(clercId));
        d.setNotaire(users.get(notaireId));
        d.setDateOuverture(ilYA(ouvertJours));
        d.setDateSignaturePrevue(signaturePrevue == null ? null : aujourdhui.plusDays(signaturePrevue));
        d.setMontantTransaction(montant);
        if (!id.equals(DOSSIER_VEDETTE)) {
            genererPieces(d, nbPiecesRecues);
        }
        return d;
    }

    /**
     * Checklist deterministe : les {@code nbRecues} premieres pieces sont validees,
     * a des dates posterieures a l'ouverture. Les pieces a duree de validite (etat
     * des droits reels) sont emises peu avant reception : un dossier qui s'enlise
     * en instruction voit donc ses pieces se perimer.
     */
    private void genererPieces(Dossier d, int nbRecues) {
        List<ModelesPieces.Modele> modeles = ModelesPieces.pour(d.getTypeActe());
        for (int i = 0; i < modeles.size(); i++) {
            PieceJustificative p = modeles.get(i).instancier();
            boolean recue = i < nbRecues;
            p.setStatut(recue ? StatutPiece.VALIDEE : i == nbRecues ? StatutPiece.DEMANDEE : StatutPiece.MANQUANTE);
            p.setDateDemande(borne(d.getDateOuverture().plusDays(2 + i)));
            if (recue) {
                p.setDateReception(borne(d.getDateOuverture().plusDays(5 + 2L * i)));
                if (p.getValiditeJours() != null) {
                    p.setDateEmission(p.getDateReception().minusDays(2));
                }
            }
            d.ajouterPiece(p);
        }
    }

    private LocalDate borne(LocalDate date) {
        return date.isAfter(aujourdhui) ? aujourdhui : date;
    }

    private static Bien bien(String designation, String tf, String adresse, String commune, int m2, UsageBien usage) {
        return new Bien(designation, tf, adresse, commune, m2, usage);
    }

    private void signe(Dossier d, int ilYaJours, ModeSignature mode) {
        d.setDateSignature(ilYA(ilYaJours));
        d.setModeSignature(mode);
    }

    private void chargerDossiers() {
        List<Dossier> liste = new ArrayList<>();

        /* --- Dossier vedette : vente immeuble Mermoz, en attente de provision --- */
        Dossier vedette = dossier(DOSSIER_VEDETTE, "2026-VI-0142", "Vente Immeuble Mermoz — Client Ndiaye",
                TypeActe.VENTE_IMMOBILIERE, StatutDossier.FUNDING, 25, "U-003", "U-001", 78, 12, 185_000_000, 0);
        vedette.setBien(bien("Immeuble R+2 a usage d'habitation, 6 appartements", "TF n° 18 742/DG",
                "Lot 214, cite Mermoz Pyrotechnie", "Dakar — Mermoz/Sacre-Coeur", 420, UsageBien.HABITATION));
        Intervenant vendeur = new Intervenant(TypePersonne.PHYSIQUE, "M. Abdoulaye Ndiaye", QualiteIntervenant.VENDEUR, "CNI 1 234 1988 00456", true);
        vendeur.setTelephone("+221 77 512 44 90");
        Intervenant acquereur = new Intervenant(TypePersonne.PHYSIQUE, "Mme Khady Gueye", QualiteIntervenant.ACQUEREUR, "CNI 1 234 1991 07731", true);
        acquereur.setTelephone("+221 78 330 12 05");
        vedette.ajouterIntervenant(vendeur);
        vedette.ajouterIntervenant(acquereur);
        vedette.ajouterIntervenant(new Intervenant(TypePersonne.MORALE, "CBAO Groupe Attijariwafa Bank", QualiteIntervenant.BANQUE, "NINEA 002 118 447", true));
        StatutPiece[] statutsVedette = {StatutPiece.VALIDEE, StatutPiece.VALIDEE, StatutPiece.VALIDEE, StatutPiece.RECUE,
                StatutPiece.VALIDEE, StatutPiece.VALIDEE, StatutPiece.VALIDEE, StatutPiece.VALIDEE, StatutPiece.RECUE,
                StatutPiece.DEMANDEE, StatutPiece.MANQUANTE};
        for (int i = 0; i < ModelesPieces.VENTE.size(); i++) {
            PieceJustificative p = ModelesPieces.VENTE.get(i).instancier();
            p.setStatut(statutsVedette[i]);
            p.setDateDemande(ilYA(72 - i * 3));
            if (p.estObtenue()) {
                p.setDateReception(ilYA(60 - i * 3));
            }
            if (p.getValiditeJours() != null) {
                p.setDateEmission(ilYA(60)); // encore valide, mais a surveiller
            }
            if (p.getCategorie().name().equals("FISCAL")) {
                p.setDateExpiration(dans(45 - i));
            }
            if (p.getStatut() == StatutPiece.MANQUANTE) {
                p.setCommentaire("Relance effectuee aupres de la CBAO — exigee pour la mise a la signature.");
            } else if (p.getStatut() == StatutPiece.DEMANDEE) {
                p.setCommentaire("Demande transmise, delai annonce 10 jours ouvres.");
            }
            vedette.ajouterPiece(p);
        }
        liste.add(vedette);

        Dossier d138 = dossier("D-0138", "2026-VI-0138", "Vente villa Almadies — Succession Camara", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.DRAFTING, 10, "U-004", "U-002", 64, 20, 420_000_000, 9);
        d138.setBien(bien("Villa individuelle R+1", "TF n° 11 208/DG", "Route des Almadies, lot 47", "Dakar — Ngor/Almadies", 610, UsageBien.HABITATION));
        liste.add(d138);

        Dossier d131 = dossier("D-0131", "2026-VI-0131", "Vente terrain Diamniadio — SCI Teranga", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.PENDING_DOCS, 90, "U-005", "U-002", 96, -9, 96_000_000, 4);
        d131.setBien(bien("Terrain nu viabilise", "TF n° 24 561/R", "Pole urbain de Diamniadio, ilot 12", "Rufisque — Diamniadio", 2400, UsageBien.TERRAIN_NU));
        liste.add(d131);

        liste.add(dossier("D-0127", "2026-DS-0127", "Constitution SARL Baobab Logistics", TypeActe.DROIT_DES_SOCIETES,
                StatutDossier.REVIEW, 8, "U-006", "U-001", 41, 6, 25_000_000, 5));

        Dossier d125 = dossier("D-0125", "2026-VI-0125", "Vente appartement Point E — Diouf/Mbaye", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.SIGNED, 7, "U-003", "U-001", 103, -8, 78_000_000, 11);
        d125.setBien(bien("Appartement F4, 3e etage", "TF n° 9 811/DG", "Rue de Fann, residence Espadon", "Dakar — Point E", 128, UsageBien.HABITATION));
        signe(d125, 7, ModeSignature.ELECTRONIQUE_GAINDE);
        d125.setGaindeStatut(StatutGainde.TRANSMIS);
        d125.setGaindeDateMiseAJour(ilYA(7));
        liste.add(d125);

        liste.add(dossier("D-0119", "2026-SU-0119", "Succession Mme Anta Fall — partage", TypeActe.SUCCESSION,
                StatutDossier.PENDING_DOCS, 140, "U-004", "U-002", 147, -24, 143_000_000, 2));

        Dossier d116 = dossier("D-0116", "2026-VI-0116", "Vente immeuble Plateau — SCI Kermel", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.POST_ACT, 20, "U-005", "U-001", 126, -31, 615_000_000, 11);
        d116.setBien(bien("Immeuble de bureaux R+4", "TF n° 3 402/DG", "Rue Vincens, Plateau", "Dakar — Plateau", 890, UsageBien.COMMERCIAL));
        signe(d116, 29, ModeSignature.ELECTRONIQUE_GAINDE);
        d116.setGaindeStatut(StatutGainde.VALIDE);
        d116.setGaindeDateMiseAJour(ilYA(27));
        d116.setGaindeReference("GAINDE-2026-118842");
        d116.setFormalitesOrganisme(OrganismeFormalites.CONSERVATION_FONCIERE);
        d116.setFormalitesDateDepot(ilYA(18));
        d116.setFormalitesReference("CF-DKR-2026-4471");
        liste.add(d116);

        Dossier d112 = dossier("D-0112", "2026-CH-0112", "Credit hypothecaire — Sy / BOA Senegal", TypeActe.CREDIT_HYPOTHECAIRE,
                StatutDossier.READY_TO_SIGN, 6, "U-006", "U-002", 58, 3, 52_000_000, 11);
        d112.ajouterIntervenant(new Intervenant(TypePersonne.PHYSIQUE, "M. Pape Sy", QualiteIntervenant.ACQUEREUR, "CNI 1 751 1984 02213", true));
        d112.ajouterIntervenant(new Intervenant(TypePersonne.MORALE, "BOA Senegal", QualiteIntervenant.BANQUE, "NINEA 001 942 210", true));
        liste.add(d112);

        Dossier d108 = dossier("D-0108", "2026-VI-0108", "Vente residence Saly — Ndour", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.DRAFTING, 30, "U-003", "U-002", 88, -4, 265_000_000, 9);
        d108.setBien(bien("Residence balneaire avec piscine", "TF n° 7 129/MB", "Saly Portudal, zone hoteliere", "Mbour — Saly", 750, UsageBien.HABITATION));
        d108.setPurgeConditionsEnCours(true);
        liste.add(d108);

        liste.add(dossier("D-0104", "2026-DF-0104", "Donation-partage famille Thiam", TypeActe.DONATION_FAMILLE,
                StatutDossier.DRAFTING, 12, "U-005", "U-001", 52, 15, 64_000_000, 5));

        Dossier d098 = dossier("D-0098", "2026-BC-0098", "Bail commercial — Centre Sea Plaza", TypeActe.BAIL_COMMERCIAL,
                StatutDossier.SIGNED, 11, "U-004", "U-002", 70, -11, 36_000_000, 11);
        signe(d098, 11, ModeSignature.PHYSIQUE);
        liste.add(d098);

        Dossier d091 = dossier("D-0091", "2026-VI-0091", "Vente villa Ouakam — Barry/Kane", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.POST_ACT, 48, "U-006", "U-001", 168, -52, 112_000_000, 11);
        d091.setBien(bien("Villa basse avec dependance", "TF n° 15 003/DG", "Cite Avion, Ouakam", "Dakar — Ouakam", 380, UsageBien.HABITATION));
        signe(d091, 50, ModeSignature.PHYSIQUE);
        liste.add(d091);

        Dossier d087 = dossier("D-0087", "2026-SU-0087", "Succession Sylla — liquidation", TypeActe.SUCCESSION,
                StatutDossier.POST_ACT, 75, "U-003", "U-002", 205, -80, 58_000_000, 5);
        signe(d087, 78, ModeSignature.PHYSIQUE);
        liste.add(d087);

        Dossier d082 = dossier("D-0082", "2026-VI-0082", "Vente parcelle Yoff — Diagne", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.POST_ACT, 90, "U-005", "U-001", 190, -96, 47_000_000, 11);
        d082.setBien(bien("Parcelle a usage mixte", "TF n° 21 774/DG", "Yoff Layene, lot 88", "Dakar — Yoff", 300, UsageBien.MIXTE));
        signe(d082, 94, ModeSignature.PHYSIQUE);
        d082.setFormalitesOrganisme(OrganismeFormalites.CONSERVATION_FONCIERE);
        d082.setFormalitesDateDepot(ilYA(60));
        d082.setFormalitesReference("CF-DKR-2026-2210");
        liste.add(d082);

        Dossier d076 = dossier("D-0076", "2026-DS-0076", "Cession de parts — Sahel Distribution SA", TypeActe.DROIT_DES_SOCIETES,
                StatutDossier.CLOSED, 120, "U-006", "U-002", 220, -140, 180_000_000, 6);
        signe(d076, 138, ModeSignature.PHYSIQUE);
        d076.setFormalitesOrganisme(OrganismeFormalites.GREFFE_RCCM);
        d076.setFormalitesDateDepot(ilYA(130));
        d076.setFormalitesReference("RCCM-SN-DKR-2026-B-0312");
        d076.setDateCloture(ilYA(120));
        liste.add(d076);

        Dossier d071 = dossier("D-0071", "2026-VI-0071", "Vente duplex Hann Mariste — Cisse", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.PENDING_DOCS, 105, "U-004", "U-001", 112, -17, 89_000_000, 5);
        d071.setBien(bien("Duplex F5", "TF n° 19 330/DG", "Hann Mariste 2, villa 118", "Dakar — Hann Bel-Air", 210, UsageBien.HABITATION));
        liste.add(d071);

        liste.add(dossier("D-0065", "2026-VI-0065", "Vente magasin Guediawaye — Ndao", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.DRAFTING, 6, "U-003", "U-002", 34, 22, 31_000_000, 9));

        Dossier d059 = dossier("D-0059", "2026-DF-0059", "Donation immobiliere — famille Seck", TypeActe.DONATION_FAMILLE,
                StatutDossier.SIGNED, 5, "U-005", "U-001", 61, -5, 41_000_000, 5);
        signe(d059, 5, ModeSignature.PHYSIQUE);
        liste.add(d059);

        Dossier d146 = dossier("D-0146", "2026-VI-0146", "Vente appartement Sacre-Coeur — Mbengue", TypeActe.VENTE_IMMOBILIERE,
                StatutDossier.DRAFT, 1, "U-004", "U-002", 1, null, 58_000_000, 0);
        d146.setBien(bien("Appartement F3, 2e etage", "TF n° 22 015/DG", "Sacre-Coeur 3, villa 9120", "Dakar — Sacre-Coeur", 96, UsageBien.HABITATION));
        d146.ajouterIntervenant(new Intervenant(TypePersonne.PHYSIQUE, "Mme Awa Mbengue", QualiteIntervenant.VENDEUR, null, false));
        liste.add(d146);

        liste.add(dossier("D-0144", "2026-DF-0144", "Donation entre epoux — Sow", TypeActe.DONATION_FAMILLE,
                StatutDossier.TAXING, 2, "U-006", "U-001", 30, 18, 35_000_000, 5));

        dossiers.saveAll(liste);
    }

    /* ------------------------------------------------------------------ *
     *  Ecritures du compte de maniement
     * ------------------------------------------------------------------ */

    private record Ecriture(String dossierId, int jours, SensEcriture sens, long montant, NatureMouvement nature,
                            String libelle, String contrepartie) {
    }

    private static Ecriture c(String d, int j, long m, NatureMouvement n, String l, String cp) {
        return new Ecriture(d, j, SensEcriture.CREDIT, m, n, l, cp);
    }

    private static Ecriture db(String d, int j, long m, NatureMouvement n, String l, String cp) {
        return new Ecriture(d, j, SensEcriture.DEBIT, m, n, l, cp);
    }

    private void chargerEcritures() {
        List<Ecriture> ecritures = new ArrayList<>(List.of(
                c("D-0142", 74, 5_000_000, NatureMouvement.PROVISION_CLIENT, "Provision sur frais — acquereur K. Gueye", "Khady Gueye"),
                c("D-0142", 41, 18_500_000, NatureMouvement.DEPOT_GARANTIE, "Depot de garantie 10 % du prix", "Khady Gueye"),
                c("D-0142", 22, 166_500_000, NatureMouvement.PRIX_VENTE, "Sequestre du solde du prix — deblocage credit CBAO", "CBAO Groupe Attijariwafa Bank"),
                db("D-0142", 19, 9_250_000, NatureMouvement.PAIEMENT_TRESOR, "Droits d'enregistrement 5 % — Recette des Domaines", "Tresor public — Recette des Domaines"),
                db("D-0142", 19, 1_850_000, NatureMouvement.PAIEMENT_TRESOR, "Conservation fonciere 1 %", "Conservation fonciere de Dakar"),
                db("D-0142", 5, 3_100_000, NatureMouvement.PAIEMENT_TIERS, "Mainlevee hypothecaire — solde credit vendeur", "Banque Atlantique Senegal"),

                c("D-0138", 55, 12_000_000, NatureMouvement.PROVISION_CLIENT, "Provision sur frais d'acte", "Heritiers Camara"),
                c("D-0138", 28, 84_000_000, NatureMouvement.DEPOT_GARANTIE, "Depot de garantie 20 %", "Acquereur — SCI Yeggo"),
                db("D-0138", 11, 4_200_000, NatureMouvement.PAIEMENT_TRESOR, "Acompte droits d'enregistrement", "Tresor public"),

                c("D-0131", 84, 6_500_000, NatureMouvement.PROVISION_CLIENT, "Provision sur frais — SCI Teranga", "SCI Teranga"),
                db("D-0131", 30, 480_000, NatureMouvement.PAIEMENT_TIERS, "Frais de bornage et geometre", "Cabinet Geo-Sahel"),

                c("D-0127", 36, 25_000_000, NatureMouvement.PROVISION_CLIENT, "Depot du capital social — SARL Baobab Logistics", "Associes Baobab Logistics"),
                db("D-0127", 14, 250_000, NatureMouvement.PAIEMENT_TRESOR, "Droits fixes RCCM et enregistrement statuts", "Greffe du Tribunal de commerce"),

                c("D-0125", 60, 78_000_000, NatureMouvement.PRIX_VENTE, "Sequestre du prix de vente", "Acquereur M. Mbaye"),
                c("D-0125", 60, 7_400_000, NatureMouvement.PROVISION_CLIENT, "Provision frais et droits", "Acquereur M. Mbaye"),
                db("D-0125", 7, 3_900_000, NatureMouvement.PAIEMENT_TRESOR, "Droits d'enregistrement", "Tresor public"),
                db("D-0125", 6, 74_000_000, NatureMouvement.VERSEMENT_VENDEUR, "Versement du prix au vendeur", "Mme A. Diouf"),
                db("D-0125", 3, 2_950_000, NatureMouvement.HONORAIRES_ETUDE, "Prelevement des emoluments — acte signe", "Etude Ndoye & Associes"),

                c("D-0119", 132, 34_000_000, NatureMouvement.PROVISION_CLIENT, "Fonds de succession centralises", "Indivision Fall"),
                db("D-0119", 118, 1_200_000, NatureMouvement.PAIEMENT_TIERS, "Reglement dettes successorales", "Creancier — Ecobank"),

                c("D-0116", 34, 45_000_000, NatureMouvement.PROVISION_CLIENT, "Provision sur frais et droits", "Acquereur — Fonds Sahel Invest"),
                c("D-0116", 33, 615_000_000, NatureMouvement.PRIX_VENTE, "Sequestre du prix — immeuble Plateau", "Acquereur — Fonds Sahel Invest"),
                db("D-0116", 27, 30_750_000, NatureMouvement.PAIEMENT_TRESOR, "Droits d'enregistrement 5 %", "Tresor public"),
                db("D-0116", 25, 6_150_000, NatureMouvement.PAIEMENT_TRESOR, "Conservation fonciere", "Conservation fonciere de Dakar"),
                db("D-0116", 21, 540_000_000, NatureMouvement.VERSEMENT_VENDEUR, "Versement du prix — SCI Kermel", "SCI Kermel"),

                c("D-0112", 44, 3_200_000, NatureMouvement.PROVISION_CLIENT, "Provision frais d'inscription hypothecaire", "M. Sy"),

                c("D-0108", 71, 26_500_000, NatureMouvement.DEPOT_GARANTIE, "Depot de garantie 10 %", "Acquereur M. Ndour"),
                c("D-0108", 40, 4_000_000, NatureMouvement.PROVISION_CLIENT, "Provision sur frais", "Acquereur M. Ndour"),

                c("D-0104", 47, 5_600_000, NatureMouvement.PROVISION_CLIENT, "Provision droits de donation", "Famille Thiam"),

                c("D-0098", 58, 3_600_000, NatureMouvement.DEPOT_GARANTIE, "Depot de garantie — bail commercial", "Preneur — Sea Plaza Retail"),
                db("D-0098", 10, 900_000, NatureMouvement.HONORAIRES_ETUDE, "Prelevement emoluments — bail signe", "Etude Ndoye & Associes"),

                c("D-0091", 162, 9_500_000, NatureMouvement.PROVISION_CLIENT, "Provision sur frais et droits", "Acquereur M. Kane"),
                c("D-0091", 160, 112_000_000, NatureMouvement.PRIX_VENTE, "Sequestre du prix — villa Ouakam", "Acquereur M. Kane"),
                db("D-0091", 150, 5_600_000, NatureMouvement.PAIEMENT_TRESOR, "Droits d'enregistrement", "Tresor public"),
                db("D-0091", 137, 88_000_000, NatureMouvement.VERSEMENT_VENDEUR, "Versement partiel au vendeur", "M. O. Barry"),

                c("D-0087", 190, 58_000_000, NatureMouvement.PROVISION_CLIENT, "Fonds de liquidation successorale", "Indivision Sylla"),
                db("D-0087", 103, 41_300_000, NatureMouvement.PAIEMENT_TIERS, "Repartition partielle entre heritiers", "Heritiers Sylla"),

                c("D-0082", 141, 4_500_000, NatureMouvement.PROVISION_CLIENT, "Provision sur frais et droits", "Acquereur Mme Diagne"),
                c("D-0082", 140, 47_000_000, NatureMouvement.PRIX_VENTE, "Sequestre du prix — parcelle Yoff", "Acquereur Mme Diagne"),
                db("D-0082", 120, 2_350_000, NatureMouvement.PAIEMENT_TRESOR, "Droits d'enregistrement", "Tresor public"),
                db("D-0082", 96, 38_000_000, NatureMouvement.VERSEMENT_VENDEUR, "Versement au vendeur — solde retenu", "M. I. Diagne"),

                c("D-0071", 105, 8_900_000, NatureMouvement.DEPOT_GARANTIE, "Depot de garantie 10 %", "Acquereur M. Cisse"),
                db("D-0071", 83, 600_000, NatureMouvement.PAIEMENT_TIERS, "Frais d'expertise immobiliere", "Cabinet Expertys"),

                c("D-0065", 29, 2_400_000, NatureMouvement.PROVISION_CLIENT, "Provision sur frais", "M. Ndao"),

                c("D-0059", 40, 3_300_000, NatureMouvement.PROVISION_CLIENT, "Provision droits de donation", "Famille Seck"),
                db("D-0059", 4, 1_230_000, NatureMouvement.PAIEMENT_TRESOR, "Droits de donation", "Tresor public")));

        ecritures.sort(Comparator.comparingInt(Ecriture::jours).reversed());
        Map<String, Long> cumuls = new HashMap<>();
        long total = 0;
        int index = 1;
        for (Ecriture e : ecritures) {
            LocalDate date = ilYA(e.jours());
            long solde = cumuls.merge(e.dossierId(), e.sens() == SensEcriture.CREDIT ? e.montant() : -e.montant(), Long::sum);
            MouvementComptable m = new MouvementComptable();
            m.setNumeroPiece("%d/%05d".formatted(date.getYear(), index));
            m.setDossier(dossiers.getReferenceById(e.dossierId()));
            m.setDateOperation(date);
            m.setDateValeur(date);
            m.setSens(e.sens());
            m.setMontant(e.montant());
            m.setNature(e.nature());
            m.setLibelle(e.libelle());
            m.setContrepartie(e.contrepartie());
            m.setMoyenPaiement(e.montant() > 10_000_000 ? MoyenPaiement.VIREMENT : MoyenPaiement.CHEQUE);
            m.setReferenceBancaire("REF%s%03d".formatted(date.toString().replace("-", ""), index));
            m.setSaisiParId("U-007");
            m.setValideParId("U-001");
            m.setDateValidation(date.atTime(10, (index * 7) % 60));
            m.setSoldeApres(solde);
            m.setRapproche(e.jours() > 5);
            mouvements.save(m);
            total += m.montantSigne();
            index++;
        }

        CompteCollectif compte = new CompteCollectif();
        compte.setId(ComptaService.COMPTE_COLLECTIF_ID);
        compte.setLibelle("Compte de maniement de fonds clients — Etude Ndoye & Associes");
        compte.setBanque("Societe Generale Senegal");
        compte.setIban("SN08 SG00 0100 0047 8812 0031 47");
        compte.setSoldeBancaire(total); // invariant I2 satisfait par construction
        compte.setDateDernierReleve(ilYA(1));
        comptes.save(compte);
    }

    /**
     * Visas des etapes deja franchies : validation du notaire, taxage fige, puis
     * certification des fonds au montant reellement encaisse sur le sequestre.
     */
    private void poserVisas() {
        for (Dossier d : dossiers.findAll()) {
            LocalDate certification = d.getDateSignature() != null ? d.getDateSignature().minusDays(3) : d.getDateEntreeStatut();
            if (d.getStatut().estAuMoins(StatutDossier.TAXING)) {
                d.setValidationNotaireParId(d.getNotaire().getId());
                LocalDate validation = switch (d.getStatut()) {
                    case TAXING -> d.getDateEntreeStatut();
                    case FUNDING -> d.getDateEntreeStatut().minusDays(3);
                    default -> certification.minusDays(7);
                };
                d.setValidationNotaireDate(validation);
            }
            if (d.getStatut().estAuMoins(StatutDossier.FUNDING)) {
                var decompte = taxation.simuler(d.getTypeActe(), d.getMontantTransaction());
                d.setFraisPrevisionnels(decompte.totaux().totalGeneral());
                d.setVersionBareme(decompte.versionBareme());
            }
            if (d.getStatut().estAuMoins(StatutDossier.READY_TO_SIGN)) {
                long encaisse = mouvements.findByDossier_IdOrderByDateOperationAscIdAsc(d.getId()).stream()
                        .filter(m -> m.getSens() == SensEcriture.CREDIT).mapToLong(MouvementComptable::getMontant).sum();
                d.setCertificationFondsParId("U-007");
                d.setCertificationFondsDate(certification);
                d.setMontantCertifie(encaisse);
            }
        }
    }

    /* ------------------------------------------------------------------ *
     *  Journal d'audit du dossier vedette
     * ------------------------------------------------------------------ */

    private record Ev(int jours, String heure, TypeEvenement type, String auteur, String libelle, Map<String, String> detail) {
    }

    private static Map<String, String> m(String... cv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < cv.length; i += 2) {
            map.put(cv[i], cv[i + 1]);
        }
        return map;
    }

    private void chargerJournalVedette() {
        List<Ev> journal = List.of(
                new Ev(78, "09:12", TypeEvenement.CREATION, "U-009", "Ouverture du dossier — vente immeuble Mermoz", m("reference", "2026-VI-0142")),
                new Ev(78, "09:20", TypeEvenement.ASSIGNATION, "U-001", "Dossier assigne a Awa Diallo (clerc redacteur)", m("apres", "U-003")),
                new Ev(77, "11:40", TypeEvenement.CHANGEMENT_STATUT, "U-003", "Passage au statut « Instruction & Pieces »", m("avant", "DRAFT", "apres", "PENDING_DOCS")),
                new Ev(74, "15:05", TypeEvenement.MOUVEMENT_FONDS, "U-007", "Encaissement provision sur frais — 5 000 000 FCFA", m("montant", "5000000")),
                new Ev(66, "08:55", TypeEvenement.PIECE_VALIDEE, "U-003", "Titre foncier TF n° 18 742/DG valide", null),
                new Ev(58, "14:22", TypeEvenement.PIECE_VALIDEE, "U-003", "Extrait d'urbanisme recu de la mairie de Mermoz", null),
                new Ev(50, "10:03", TypeEvenement.CHANGEMENT_STATUT, "U-003", "Passage au statut « Redaction en cours »", m("avant", "PENDING_DOCS", "apres", "DRAFTING")),
                new Ev(41, "16:47", TypeEvenement.MOUVEMENT_FONDS, "U-007", "Depot de garantie 10 % encaisse — 18 500 000 FCFA", m("montant", "18500000")),
                new Ev(33, "09:31", TypeEvenement.EDITION_DOCUMENT, "U-003", "Projet d'acte de vente edite (v2) et transmis aux parties", null),
                new Ev(31, "17:02", TypeEvenement.CHANGEMENT_STATUT, "U-003", "Passage au statut « Validation Titulaire »", m("avant", "DRAFTING", "apres", "REVIEW")),
                new Ev(28, "10:15", TypeEvenement.VALIDATION_NOTAIRE, "U-001", "Projet d'acte valide par le notaire", null),
                new Ev(28, "10:16", TypeEvenement.CHANGEMENT_STATUT, "U-001", "Passage au statut « Taxage previsionnel »", m("avant", "REVIEW", "apres", "TAXING")),
                new Ev(25, "12:40", TypeEvenement.CHANGEMENT_STATUT, "U-007", "Passage au statut « En attente de provision »", m("avant", "TAXING", "apres", "FUNDING")),
                new Ev(22, "11:18", TypeEvenement.MOUVEMENT_FONDS, "U-007", "Sequestre du solde du prix — 166 500 000 FCFA (deblocage CBAO)", m("montant", "166500000")),
                new Ev(19, "09:02", TypeEvenement.MOUVEMENT_FONDS, "U-007", "Paiement des droits d'enregistrement — 9 250 000 FCFA", null),
                new Ev(12, "17:26", TypeEvenement.TENTATIVE_BLOQUEE, "U-007", "Virement refuse : solde auxiliaire insuffisant (controle d'etancheite)",
                        m("montantDemande", "180000000", "soldeDisponible", "178900000", "regle", "SOLDE_INSUFFISANT")),
                new Ev(8, "16:05", TypeEvenement.TENTATIVE_BLOQUEE, "U-007", "Transition refusee vers « Pret pour signature »",
                        m("vers", "READY_TO_SIGN", "motifs", "PIECES_MANQUANTES, FONDS_INSUFFISANTS")),
                new Ev(6, "10:44", TypeEvenement.PIECE_REJETEE, "U-001", "Mainlevee d'hypotheque non conforme — relance CBAO", null),
                new Ev(5, "15:09", TypeEvenement.MOUVEMENT_FONDS, "U-007", "Reglement mainlevee hypothecaire — 3 100 000 FCFA", null));

        // Le statut porte par chaque evenement est reconstitue a partir des changements de statut.
        StatutDossier courant = StatutDossier.DRAFT;
        for (Ev e : journal) {
            LocalDateTime horodatage = LocalDateTime.of(ilYA(e.jours()), LocalTime.parse(e.heure()));
            evenements.save(new EvenementAudit(DOSSIER_VEDETTE, horodatage, e.type(), e.auteur(), courant, e.libelle(), e.detail()));
            if (e.type() == TypeEvenement.CHANGEMENT_STATUT) {
                courant = StatutDossier.valueOf(e.detail().get("apres"));
            }
        }
    }
}
