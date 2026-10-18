package sn.afrierp.notarius.clients;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.common.RessourceIntrouvableException;
import sn.afrierp.notarius.compta.ComptaService;
import sn.afrierp.notarius.domain.Client;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.Intervenant;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.TypePersonne;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.ClientRepository;
import sn.afrierp.notarius.repository.IntervenantRepository;

/**
 * Fichier clients de l'etude. Chaque partie a un acte est rattachee a une fiche
 * client (retrouvee par son identifiant officiel, a defaut par son nom) : la
 * fiche retrace ainsi l'ensemble des dossiers de la personne, quel que soit son
 * role dans chacun.
 */
@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clients;
    private final IntervenantRepository intervenants;
    private final ComptaService compta;
    private final Clock clock;

    public ClientService(ClientRepository clients, IntervenantRepository intervenants, ComptaService compta, Clock clock) {
        this.clients = clients;
        this.intervenants = intervenants;
        this.compta = compta;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ *
     *  Vues
     * ------------------------------------------------------------------ */

    public record ClientResume(String id, String type, String nom, String identifiant, String telephone, String email,
                               boolean kycValide, long nbDossiers, long nbEnCours, LocalDate dernierDossier) {
    }

    public record DossierDuClient(String dossierId, String reference, String intitule, String typeActe, String domaine,
                                  String statut, String statutLibelle, String qualite, String qualiteLibelle,
                                  LocalDate dateOuverture, LocalDate dateSignature, String numeroRepertoire,
                                  LocalDate dateArchivage, String notaire, String clerc, long solde) {
    }

    public record PersonneLiee(String clientId, String nom, String type, long dossiersCommuns) {
    }

    public record FicheClient(Client client, long nbDossiers, long nbEnCours, long nbActesSignes, long fondsDetenus,
                              List<DossierDuClient> dossiers, List<PersonneLiee> personnesLiees) {
    }

    public List<ClientResume> lister(String recherche) {
        String q = recherche == null ? "" : normaliser(recherche);
        Map<String, List<Intervenant>> parClient = intervenants.findAll().stream()
                .filter(i -> i.getClient() != null)
                .collect(Collectors.groupingBy(i -> i.getClient().getId()));
        return clients.findAllByOrderByNom().stream()
                .filter(c -> q.isEmpty() || normaliser(c.getNom()).contains(q)
                        || (c.getIdentifiant() != null && normaliser(c.getIdentifiant()).contains(q))
                        || (c.getTelephone() != null && c.getTelephone().replace(" ", "").contains(q.replace(" ", ""))))
                .map(c -> {
                    List<Dossier> ds = parClient.getOrDefault(c.getId(), List.of()).stream()
                            .map(Intervenant::getDossier).distinct().toList();
                    return new ClientResume(c.getId(), c.getType().name(), c.getNom(), c.getIdentifiant(),
                            c.getTelephone(), c.getEmail(), c.isKycValide(), ds.size(),
                            ds.stream().filter(d -> d.getStatut() != StatutDossier.CLOSED).count(),
                            ds.stream().map(Dossier::getDateOuverture).filter(Objects::nonNull)
                                    .max(Comparator.naturalOrder()).orElse(null));
                })
                .toList();
    }

    public FicheClient fiche(String id) {
        Client client = client(id);
        List<Intervenant> siennes = intervenants.findByClient_Id(id);
        Function<Utilisateur, String> nom = u -> u == null ? null : u.nomComplet();
        List<DossierDuClient> dossiers = siennes.stream()
                .map(i -> {
                    Dossier d = i.getDossier();
                    return new DossierDuClient(d.getId(), d.getReference(), d.getIntitule(), d.getTypeActe().getLibelle(),
                            d.getTypeActe().getDomaine().getLibelle(), d.getStatut().name(), d.getStatut().getLibelle(),
                            i.getQualite().name(), i.getQualite().getLibelle(), d.getDateOuverture(), d.getDateSignature(),
                            d.getNumeroRepertoire(), d.getDateArchivage(), nom.apply(d.getNotaire()), nom.apply(d.getClerc()),
                            compta.solde(d.getId()));
                })
                .sorted(Comparator.comparing(DossierDuClient::dateOuverture, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<String> idsDossiers = dossiers.stream().map(DossierDuClient::dossierId).distinct().toList();
        Map<Client, Long> communs = idsDossiers.isEmpty() ? Map.of() : intervenants.findByDossier_IdIn(idsDossiers).stream()
                .filter(i -> i.getClient() != null && !i.getClient().getId().equals(id))
                .collect(Collectors.groupingBy(Intervenant::getClient, LinkedHashMap::new,
                        Collectors.mapping(i -> i.getDossier().getId(), Collectors.collectingAndThen(Collectors.toSet(), s -> (long) s.size()))));
        List<PersonneLiee> liees = communs.entrySet().stream()
                .map(e -> new PersonneLiee(e.getKey().getId(), e.getKey().getNom(), e.getKey().getType().name(), e.getValue()))
                .sorted(Comparator.comparingLong(PersonneLiee::dossiersCommuns).reversed().thenComparing(PersonneLiee::nom))
                .toList();
        long enCours = dossiers.stream().filter(d -> !d.statut().equals(StatutDossier.CLOSED.name()))
                .map(DossierDuClient::dossierId).distinct().count();
        long signes = dossiers.stream().filter(d -> d.dateSignature() != null).map(DossierDuClient::dossierId).distinct().count();
        long fonds = dossiers.stream().filter(d -> !d.statut().equals(StatutDossier.CLOSED.name()))
                .collect(Collectors.toMap(DossierDuClient::dossierId, DossierDuClient::solde, (a, b) -> a))
                .values().stream().mapToLong(Long::longValue).sum();
        return new FicheClient(client, idsDossiers.size(), enCours, signes, fonds, dossiers, liees);
    }

    /* ------------------------------------------------------------------ *
     *  Ecriture
     * ------------------------------------------------------------------ */

    public record DonneesClient(TypePersonne type, String nom, String identifiant, String telephone, String email,
                                String adresse, String regimeMatrimonial, String notes) {
    }

    @Transactional
    public Client creer(DonneesClient donnees, Utilisateur auteur) {
        exigerSaisie(auteur);
        if (donnees.identifiant() != null && !donnees.identifiant().isBlank()
                && clients.findFirstByIdentifiantIgnoreCase(donnees.identifiant().trim()).isPresent()) {
            throw new RegleMetierException("CLIENT_EXISTANT", "Un client porte déjà l'identifiant " + donnees.identifiant().trim() + ".");
        }
        Client c = new Client(prochainId(), donnees.type(), donnees.nom().trim(), vide(donnees.identifiant()), LocalDate.now(clock));
        appliquer(c, donnees);
        return clients.save(c);
    }

    @Transactional
    public Client modifier(String id, DonneesClient donnees, Utilisateur auteur) {
        exigerSaisie(auteur);
        Client c = client(id);
        c.setType(donnees.type());
        c.setNom(donnees.nom().trim());
        c.setIdentifiant(vide(donnees.identifiant()));
        appliquer(c, donnees);
        return c;
    }

    /**
     * Rattache une partie a sa fiche client, creee au besoin. Les coordonnees
     * connues completent la fiche sans ecraser ce qui y figure deja.
     */
    @Transactional
    public Client rattacher(Intervenant i) {
        if (i.getClient() != null) {
            return i.getClient();
        }
        Client c = (i.getIdentifiant() != null && !i.getIdentifiant().isBlank()
                ? clients.findFirstByIdentifiantIgnoreCase(i.getIdentifiant().trim())
                : clients.findFirstByNomIgnoreCase(i.getNom().trim()))
                .orElseGet(() -> clients.save(new Client(prochainId(), i.getType(), i.getNom().trim(),
                        vide(i.getIdentifiant()), LocalDate.now(clock))));
        if (c.getTelephone() == null) {
            c.setTelephone(i.getTelephone());
        }
        if (c.getEmail() == null) {
            c.setEmail(i.getEmail());
        }
        if (c.getAdresse() == null) {
            c.setAdresse(i.getAdresse());
        }
        c.setKycValide(c.isKycValide() || i.isKycValide());
        i.setClient(c);
        return c;
    }

    /** Pre-remplit une partie a partir d'une fiche client existante. */
    public Intervenant depuisClient(String clientId, Intervenant i) {
        Client c = client(clientId);
        i.setType(c.getType());
        i.setNom(c.getNom());
        i.setIdentifiant(c.getIdentifiant());
        i.setTelephone(c.getTelephone());
        i.setEmail(c.getEmail());
        i.setAdresse(c.getAdresse());
        i.setClient(c);
        return i;
    }

    public Client client(String id) {
        return clients.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Client " + id + " introuvable."));
    }

    private void appliquer(Client c, DonneesClient d) {
        c.setTelephone(vide(d.telephone()));
        c.setEmail(vide(d.email()));
        c.setAdresse(vide(d.adresse()));
        c.setRegimeMatrimonial(vide(d.regimeMatrimonial()));
        c.setNotes(vide(d.notes()));
    }

    private static void exigerSaisie(Utilisateur auteur) {
        if (auteur.getRole() == Role.ARCHIVISTE || auteur.getRole() == Role.FORMALISTE) {
            throw new RegleMetierException("ROLE_NON_HABILITE", "La tenue du fichier clients est réservée à l'accueil, aux clercs, "
                    + "au comptable et aux notaires.");
        }
    }

    private String prochainId() {
        long n = clients.count() + 1;
        String id = "C-%04d".formatted(n);
        while (clients.existsById(id)) {
            id = "C-%04d".formatted(++n);
        }
        return id;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normaliser(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
