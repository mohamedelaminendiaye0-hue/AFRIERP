package sn.afrierp.notarius.compta;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.audit.AuditService;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.common.RessourceIntrouvableException;
import sn.afrierp.notarius.compta.ComptaRecords.AlerteConformite;
import sn.afrierp.notarius.compta.ComptaRecords.ControleEtancheite;
import sn.afrierp.notarius.compta.ComptaRecords.FondsImmobilise;
import sn.afrierp.notarius.compta.ComptaRecords.LigneReleve;
import sn.afrierp.notarius.compta.ComptaRecords.OrdreVirementCdc;
import sn.afrierp.notarius.compta.ComptaRecords.VerdictVirement;
import sn.afrierp.notarius.domain.CompteCollectif;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.MoyenPaiement;
import sn.afrierp.notarius.domain.MouvementComptable;
import sn.afrierp.notarius.domain.NatureMouvement;
import sn.afrierp.notarius.domain.NiveauAlerte;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.SensEcriture;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.StatutMouvement;
import sn.afrierp.notarius.domain.TypeEvenement;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.CompteCollectifRepository;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.MouvementComptableRepository;

/**
 * COEUR REGLEMENTAIRE — maniement de fonds clients et conformite CDC.
 *
 * <ul>
 *   <li>I1 : aucun sous-compte auxiliaire ne peut etre debiteur ;</li>
 *   <li>I2 : somme des sous-comptes = solde bancaire collectif ;</li>
 *   <li>I3 : fonds immobilises au-dela du seuil : transfert CDC ou justification.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ComptaService {

    public static final String COMPTE_COLLECTIF_ID = "CPT-MANIEMENT-01";

    private final MouvementComptableRepository mouvements;
    private final DossierRepository dossiers;
    private final CompteCollectifRepository comptes;
    private final AuditService audit;
    private final Clock clock;

    public ComptaService(MouvementComptableRepository mouvements, DossierRepository dossiers,
                         CompteCollectifRepository comptes, AuditService audit, Clock clock) {
        this.mouvements = mouvements;
        this.dossiers = dossiers;
        this.comptes = comptes;
        this.audit = audit;
        this.clock = clock;
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }

    public CompteCollectif compteCollectif() {
        return comptes.findById(COMPTE_COLLECTIF_ID)
                .orElseThrow(() -> new RessourceIntrouvableException("Compte de maniement introuvable."));
    }

    /* ------------------------------------------------------------------ *
     *  Sous-comptes derives des ecritures
     * ------------------------------------------------------------------ */

    public List<SousCompte> sousComptes() {
        Map<String, List<MouvementComptable>> parDossier = new LinkedHashMap<>();
        for (MouvementComptable m : mouvements.findAllByOrderByDateOperationAscIdAsc()) {
            if (m.getStatut() == StatutMouvement.VALIDE) {
                parDossier.computeIfAbsent(m.getDossierId(), k -> new ArrayList<>()).add(m);
            }
        }
        return parDossier.values().stream().map(ComptaService::versSousCompte).toList();
    }

    public Optional<SousCompte> sousCompte(String dossierId) {
        List<MouvementComptable> lignes = mouvementsValides(dossierId);
        return lignes.isEmpty() ? Optional.empty() : Optional.of(versSousCompte(lignes));
    }

    public List<MouvementComptable> mouvementsValides(String dossierId) {
        return mouvements.findByDossier_IdOrderByDateOperationAscIdAsc(dossierId).stream()
                .filter(m -> m.getStatut() == StatutMouvement.VALIDE).toList();
    }

    public long solde(String dossierId) {
        return mouvementsValides(dossierId).stream().mapToLong(MouvementComptable::montantSigne).sum();
    }

    private static SousCompte versSousCompte(List<MouvementComptable> lignes) {
        Dossier d = lignes.get(0).getDossier();
        long solde = lignes.stream().mapToLong(MouvementComptable::montantSigne).sum();
        LocalDate ouverture = lignes.stream().map(MouvementComptable::getDateOperation).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate dernier = lignes.stream().map(MouvementComptable::getDateOperation).max(Comparator.naturalOrder()).orElseThrow();
        return new SousCompte(d.getId(), d.getReference(), d.getIntitule(), solde, ouverture, dernier);
    }

    /* ------------------------------------------------------------------ *
     *  I1 / I2 — controle d'etancheite
     * ------------------------------------------------------------------ */

    public ControleEtancheite controlerEtancheite() {
        CompteCollectif compte = compteCollectif();
        List<SousCompte> sousComptes = sousComptes();
        long somme = sousComptes.stream().mapToLong(SousCompte::solde).sum();
        long ecart = compte.getSoldeBancaire() - somme;
        List<SousCompte> debiteurs = sousComptes.stream().filter(sc -> sc.solde() < 0).toList();
        return new ControleEtancheite(compte.getSoldeBancaire(), somme, ecart, ecart == 0 && debiteurs.isEmpty(),
                debiteurs, aujourdhui());
    }

    /* ------------------------------------------------------------------ *
     *  Autorisation d'un decaissement — interdiction stricte du solde debiteur
     * ------------------------------------------------------------------ */

    public static boolean habiliteMouvementsFonds(Utilisateur u) {
        return u.getRole() == Role.COMPTABLE_TAXATEUR || u.getRole().estNotaireEnTitre();
    }

    public VerdictVirement autoriserVirement(Dossier dossier, long montant, NatureMouvement nature, Utilisateur utilisateur) {
        Optional<SousCompte> sc = sousCompte(dossier.getId());
        long solde = sc.map(SousCompte::solde).orElse(0L);
        long manquant = Math.max(0, montant - solde);

        if (sc.isEmpty()) {
            return new VerdictVirement(false, "SOUS_COMPTE_INTROUVABLE", "Aucun sous-compte auxiliaire rattaché à ce dossier.", solde, montant, manquant);
        }
        if (montant <= 0) {
            return new VerdictVirement(false, "MONTANT_INVALIDE", "Saisir un montant strictement positif.", solde, montant, manquant);
        }
        if (!habiliteMouvementsFonds(utilisateur)) {
            return new VerdictVirement(false, "HABILITATION_MANQUANTE", "Opération réservée aux profils habilités au maniement de fonds.", solde, montant, manquant);
        }
        if (nature == NatureMouvement.VERSEMENT_VENDEUR && !dossier.getStatut().estAuMoins(StatutDossier.SIGNED)) {
            return new VerdictVirement(false, "DOSSIER_NON_SIGNE", "Le versement du prix au vendeur suppose un acte signé.", solde, montant, manquant);
        }
        Bareme.ParametresActe param = Bareme.parametres(dossier.getTypeActe());
        if (nature == NatureMouvement.VERSEMENT_VENDEUR && param.delaiOppositionJours() != null) {
            // Cession de fonds de commerce : le prix reste sequestre pendant le delai d'opposition
            // des creanciers, qui court a compter de la publication de l'avis.
            Optional<LocalDate> publication = dossier.formalite(OrganismeFormalites.PUBLICATION_LEGALE)
                    .map(f -> f.getDateAchevement());
            if (publication.isEmpty()) {
                return new VerdictVirement(false, "PUBLICATION_REQUISE",
                        "Prix séquestré : l'avis de cession doit être publié avant toute remise du prix au vendeur.", solde, montant, manquant);
            }
            LocalDate finDelai = publication.get().plusDays(param.delaiOppositionJours());
            if (aujourdhui().isBefore(finDelai)) {
                return new VerdictVirement(false, "DELAI_OPPOSITION_EN_COURS", String.format(Locale.FRANCE,
                        "Délai d'opposition des créanciers en cours jusqu'au %s : le prix reste séquestré.",
                        finDelai.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))), solde, montant, manquant);
            }
        }
        if (nature == NatureMouvement.DEBLOCAGE_CAPITAL) {
            if (param.sequestre() != Bareme.Sequestre.CAPITAL) {
                return new VerdictVirement(false, "NATURE_INVALIDE", "Aucun capital séquestré sur ce type d'acte.", solde, montant, manquant);
            }
            if (dossier.formalite(OrganismeFormalites.GREFFE_RCCM).map(f -> !f.estAchevee()).orElse(true)) {
                return new VerdictVirement(false, "IMMATRICULATION_REQUISE",
                        "Le capital n'est libéré qu'une fois la société immatriculée au RCCM.", solde, montant, manquant);
            }
        }
        if (montant > solde) {
            return new VerdictVirement(false, "SOLDE_INSUFFISANT",
                    "Virement bloqué : le solde auxiliaire est insuffisant. Un sous-compte client ne peut jamais devenir débiteur.",
                    solde, montant, manquant);
        }
        return new VerdictVirement(true, null, "Contrôle d'étanchéité validé — virement autorisé.", solde, montant, 0);
    }

    /**
     * Emet un decaissement. Un refus est trace au journal ({@code TENTATIVE_BLOQUEE})
     * puis leve une {@link RegleMetierException} : aucune ecriture n'est passee.
     */
    @Transactional
    public MouvementComptable executerVirement(Dossier dossier, long montant, NatureMouvement nature,
                                               String beneficiaire, Utilisateur utilisateur) {
        if (nature == null || !nature.estDecaissement()) {
            throw new RegleMetierException("NATURE_INVALIDE", "La nature choisie n'est pas un décaissement.");
        }
        VerdictVirement verdict = autoriserVirement(dossier, montant, nature, utilisateur);
        if (!verdict.autorise()) {
            audit.tracerTentativeBloquee(dossier, utilisateur, "Virement refusé : " + verdict.message(), Map.of(
                    "montantDemande", Long.toString(montant),
                    "soldeDisponible", Long.toString(verdict.soldeDisponible()),
                    "regle", verdict.code()));
            throw new RegleMetierException(verdict.code(), verdict.message());
        }
        MouvementComptable m = ecrire(dossier, SensEcriture.DEBIT, montant, nature, "Virement — " + beneficiaire,
                beneficiaire, utilisateur);
        CompteCollectif compte = compteCollectif();
        compte.setSoldeBancaire(compte.getSoldeBancaire() - montant);
        audit.tracer(dossier, TypeEvenement.MOUVEMENT_FONDS, utilisateur,
                String.format(Locale.FRANCE, "Virement émis vers %s — %,d FCFA", beneficiaire, montant),
                Map.of("nature", nature.name(), "montant", Long.toString(montant), "piece", m.getNumeroPiece()));
        return m;
    }

    /** Passe une ecriture validee et calcule le solde apres (controle d'etancheite). */
    @Transactional
    public MouvementComptable ecrire(Dossier dossier, SensEcriture sens, long montant, NatureMouvement nature,
                                     String libelle, String contrepartie, Utilisateur auteur) {
        MouvementComptable m = new MouvementComptable();
        LocalDate date = aujourdhui();
        m.setDossier(dossier);
        m.setNumeroPiece("%d/%05d".formatted(date.getYear(), mouvements.count() + 1));
        m.setDateOperation(date);
        m.setDateValeur(date);
        m.setSens(sens);
        m.setMontant(montant);
        m.setNature(nature);
        m.setLibelle(libelle);
        m.setContrepartie(contrepartie);
        m.setMoyenPaiement(MoyenPaiement.VIREMENT);
        m.setReferenceBancaire("REF" + date.toString().replace("-", "") + (mouvements.count() + 1));
        m.setSaisiParId(auteur.getId());
        m.setValideParId(auteur.getId());
        m.setDateValidation(LocalDateTime.now(clock));
        m.setSoldeApres(solde(dossier.getId()) + (sens == SensEcriture.CREDIT ? montant : -montant));
        return mouvements.save(m);
    }

    /* ------------------------------------------------------------------ *
     *  I3 — immobilisation des fonds au-dela du seuil CDC
     * ------------------------------------------------------------------ */

    public List<FondsImmobilise> detecterFondsImmobilises() {
        LocalDate ref = aujourdhui();
        return sousComptes().stream()
                .map(sc -> {
                    long anciennete = ChronoUnit.DAYS.between(sc.dateDernierMouvement(), ref);
                    return new FondsImmobilise(sc, anciennete, sc.solde(), anciennete >= Bareme.CDC_SEUIL_IMMOBILISATION_JOURS);
                })
                .filter(f -> f.montant() > Bareme.CDC_SEUIL_MONTANT_MINIMUM && f.ancienneteJours() >= Bareme.CDC_SEUIL_PRE_ALERTE_JOURS)
                .sorted(Comparator.comparingLong(FondsImmobilise::ancienneteJours).reversed())
                .toList();
    }

    public List<AlerteConformite> alertesConformite() {
        LocalDate ref = aujourdhui();
        List<AlerteConformite> alertes = new ArrayList<>();
        for (FondsImmobilise f : detecterFondsImmobilises()) {
            NiveauAlerte niveau = f.transfertObligatoire()
                    ? (f.ancienneteJours() >= 120 ? NiveauAlerte.CRITIQUE : NiveauAlerte.SERIEUX)
                    : NiveauAlerte.VIGILANCE;
            String message = f.transfertObligatoire()
                    ? "Fonds immobilisés depuis %d jours : transfert CDC obligatoire ou justification tracée.".formatted(f.ancienneteJours())
                    : "Pré-alerte : %d jours d'immobilisation, seuil à %d jours.".formatted(f.ancienneteJours(), Bareme.CDC_SEUIL_IMMOBILISATION_JOURS);
            alertes.add(new AlerteConformite("ALR-CDC-" + f.sousCompte().dossierId(), f.sousCompte().dossierId(),
                    f.sousCompte().reference(), f.sousCompte().libelle(), niveau, "CDC_90J", message, f.montant(),
                    f.ancienneteJours(), f.transfertObligatoire() ? "TRANSFERT_CDC" : "JUSTIFIER", ref));
        }
        for (SousCompte sc : sousComptes()) {
            if (sc.solde() < 0) {
                alertes.add(new AlerteConformite("ALR-DEB-" + sc.dossierId(), sc.dossierId(), sc.reference(), sc.libelle(),
                        NiveauAlerte.CRITIQUE, "SOLDE_DEBITEUR", "Solde auxiliaire débiteur détecté — régularisation immédiate requise.",
                        sc.solde(), null, "REGULARISER", ref));
            }
        }
        alertes.sort(Comparator.comparing(AlerteConformite::niveau));
        return alertes;
    }

    public boolean alerteCdc(String dossierId) {
        return detecterFondsImmobilises().stream()
                .anyMatch(f -> f.transfertObligatoire() && f.sousCompte().dossierId().equals(dossierId));
    }

    /* ------------------------------------------------------------------ *
     *  Releve et ordre de virement CDC
     * ------------------------------------------------------------------ */

    public List<LigneReleve> releve(String dossierId) {
        long cumul = 0;
        List<LigneReleve> lignes = new ArrayList<>();
        for (MouvementComptable m : mouvementsValides(dossierId)) {
            cumul += m.montantSigne();
            lignes.add(LigneReleve.de(m, cumul));
        }
        return lignes;
    }

    public OrdreVirementCdc ordreVirementCdc(String dossierId) {
        LocalDate ref = aujourdhui();
        FondsImmobilise f = detecterFondsImmobilises().stream()
                .filter(x -> x.sousCompte().dossierId().equals(dossierId)).findFirst()
                .orElseThrow(() -> new RegleMetierException("AUCUN_FONDS_IMMOBILISE", "Aucun fonds immobilisé à consigner sur ce dossier."));
        return new OrdreVirementCdc("OV-CDC-" + ref.toString().replace("-", "") + "-" + dossierId, ref,
                "Caisse des Dépôts et Consignations",
                "Consignation de fonds immobilisés — dossier " + f.sousCompte().reference(), f.montant(),
                f.sousCompte().reference(), f.ancienneteJours(), Bareme.CDC_REFERENCE,
                "Paramétrage à valider juridiquement avant exploitation — seuil d'immobilisation configuré à %d jours."
                        .formatted(Bareme.CDC_SEUIL_IMMOBILISATION_JOURS));
    }

    public Dossier dossier(String id) {
        return dossiers.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Dossier " + id + " introuvable."));
    }
}
