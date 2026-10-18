package sn.afrierp.notarius.admin;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.common.RessourceIntrouvableException;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.TypeEvenementAdmin;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.UtilisateurRepository;

/**
 * Gestion des comptes par le super administrateur. Les comptes ne sont jamais
 * supprimés (traçabilité des actes signés) : on les désactive. Les règles
 * protègent la continuité de l'étude : un seul notaire titulaire actif, au moins
 * un super administrateur, aucun dossier en cours laissé sans responsable.
 */
@Service
@Transactional
public class CompteService {

    private final UtilisateurRepository utilisateurs;
    private final DossierRepository dossiers;
    private final PasswordEncoder encodeur;
    private final PolitiqueSecurite politique;
    private final JournalAdmin journal;
    private final Clock clock;

    public CompteService(UtilisateurRepository utilisateurs, DossierRepository dossiers, PasswordEncoder encodeur,
                         PolitiqueSecurite politique, JournalAdmin journal, Clock clock) {
        this.utilisateurs = utilisateurs;
        this.dossiers = dossiers;
        this.encodeur = encodeur;
        this.politique = politique;
        this.journal = journal;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ *
     *  Vues
     * ------------------------------------------------------------------ */

    public record CompteVue(String id, String prenom, String nom, String nomComplet, String email, Role role,
                            String roleLibelle, String initiales, boolean actif, boolean verrouille,
                            LocalDateTime verrouilleJusqua, boolean doitChangerMotDePasse, LocalDateTime derniereConnexion,
                            LocalDate dateEntree, LocalDate desactiveLe, String referenceNomination,
                            String notaireResponsableId, String notaireResponsableNom, long dossiersActifs) {
    }

    /** Résultat d'une création ou d'une réinitialisation : le mot de passe provisoire n'est montré qu'une fois. */
    public record CompteEtMotDePasse(CompteVue compte, String motDePasseProvisoire) {
    }

    public record SaisieCompte(String prenom, String nom, String email, Role role, LocalDate dateEntree,
                               String referenceNomination, String notaireResponsableId) {
    }

    @Transactional(readOnly = true)
    public List<CompteVue> lister() {
        List<Dossier> enCours = dossiersEnCours();
        return utilisateurs.findAll().stream()
                .sorted(Comparator.comparing(Utilisateur::isActif).reversed()
                        .thenComparing(u -> u.getRole().ordinal()).thenComparing(Utilisateur::getNom))
                .map(u -> vue(u, enCours))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompteVue compte(String id) {
        return vue(utilisateur(id), dossiersEnCours());
    }

    /* ------------------------------------------------------------------ *
     *  Actions
     * ------------------------------------------------------------------ */

    public CompteEtMotDePasse creer(SaisieCompte s, Utilisateur admin) {
        List<Blocage> blocages = controlerSaisie(s, null);
        if (!blocages.isEmpty()) {
            throw new RegleMetierException(blocages);
        }
        Utilisateur u = new Utilisateur(prochainId(), s.prenom().trim(), s.nom().trim(), s.role(),
                initiales(s.prenom(), s.nom()), s.dateEntree() != null ? s.dateEntree() : LocalDate.now(clock));
        u.setEmail(s.email().trim().toLowerCase(Locale.ROOT));
        appliquerFonction(u, s);
        String provisoire = politique.provisoire();
        u.setMotDePasse(encodeur.encode(provisoire));
        u.setDoitChangerMotDePasse(politique.parametres().isChangementObligatoire());
        utilisateurs.save(u);
        journal.tracer(TypeEvenementAdmin.COMPTE_CREE, admin, u, u.getRole().getLibelle() + " · " + u.getEmail());
        return new CompteEtMotDePasse(compte(u.getId()), provisoire);
    }

    public CompteVue modifier(String id, SaisieCompte s, Utilisateur admin) {
        Utilisateur u = utilisateur(id);
        List<Blocage> blocages = controlerSaisie(s, u);
        if (u.getRole() != s.role()) {
            long enCours = dossiersDe(u);
            if (enCours > 0) {
                blocages.add(new Blocage("DOSSIERS_EN_COURS", "%s a %d dossier(s) en cours : réassignez-les avant de changer son profil."
                        .formatted(u.nomComplet(), enCours)));
            }
            if (u.getRole() == Role.SUPER_ADMIN && autresSuperAdminsActifs(u) == 0) {
                blocages.add(new Blocage("DERNIER_ADMINISTRATEUR", "La plateforme doit garder au moins un super administrateur actif."));
            }
            if (u.getId().equals(admin.getId())) {
                blocages.add(new Blocage("SOI_MEME", "Vous ne pouvez pas changer votre propre profil."));
            }
        }
        if (!blocages.isEmpty()) {
            throw new RegleMetierException(blocages);
        }
        List<String> changements = new ArrayList<>();
        if (!u.getEmail().equalsIgnoreCase(s.email().trim())) {
            changements.add("e-mail : " + u.getEmail() + " → " + s.email().trim().toLowerCase(Locale.ROOT));
        }
        if (u.getRole() != s.role()) {
            changements.add("profil : " + u.getRole().getLibelle() + " → " + s.role().getLibelle());
        }
        u.setPrenom(s.prenom().trim());
        u.setNom(s.nom().trim());
        u.setInitiales(initiales(s.prenom(), s.nom()));
        u.setEmail(s.email().trim().toLowerCase(Locale.ROOT));
        u.setRole(s.role());
        if (s.dateEntree() != null) {
            u.setDateEntree(s.dateEntree());
        }
        appliquerFonction(u, s);
        if (changements.stream().anyMatch(c -> c.startsWith("profil"))) {
            u.setVersionJeton(u.getVersionJeton() + 1); // les droits changent : nouvelle connexion exigée
        }
        journal.tracer(TypeEvenementAdmin.COMPTE_MODIFIE, admin, u, changements.isEmpty() ? "Coordonnées mises à jour" : String.join(" · ", changements));
        return compte(id);
    }

    public CompteVue desactiver(String id, Utilisateur admin) {
        Utilisateur u = utilisateur(id);
        List<Blocage> blocages = new ArrayList<>();
        if (!u.isActif()) {
            blocages.add(new Blocage("DEJA_DESACTIVE", "Ce compte est déjà désactivé."));
        }
        if (u.getId().equals(admin.getId())) {
            blocages.add(new Blocage("SOI_MEME", "Vous ne pouvez pas désactiver votre propre compte."));
        }
        if (u.getRole() == Role.SUPER_ADMIN && autresSuperAdminsActifs(u) == 0) {
            blocages.add(new Blocage("DERNIER_ADMINISTRATEUR", "La plateforme doit garder au moins un super administrateur actif."));
        }
        long enCours = dossiersDe(u);
        if (enCours > 0) {
            blocages.add(new Blocage("DOSSIERS_EN_COURS", "%d dossier(s) en cours sont confiés à %s : faites-les réassigner par le principal clerc avant de désactiver le compte."
                    .formatted(enCours, u.nomComplet())));
        }
        long salaries = utilisateurs.findAll().stream()
                .filter(x -> x.isActif() && x.getNotaireResponsable() != null && x.getNotaireResponsable().getId().equals(u.getId()))
                .count();
        if (salaries > 0) {
            blocages.add(new Blocage("NOTAIRE_RESPONSABLE", "%s est responsable de %d notaire(s) salarié(s) actif(s) : désignez un autre responsable d'abord."
                    .formatted(u.nomComplet(), salaries)));
        }
        if (!blocages.isEmpty()) {
            throw new RegleMetierException(blocages);
        }
        u.setActif(false);
        u.setDesactiveLe(LocalDate.now(clock));
        u.setVersionJeton(u.getVersionJeton() + 1);
        journal.tracer(TypeEvenementAdmin.COMPTE_DESACTIVE, admin, u, "Sessions en cours fermées");
        return compte(id);
    }

    public CompteVue reactiver(String id, Utilisateur admin) {
        Utilisateur u = utilisateur(id);
        if (u.isActif()) {
            throw new RegleMetierException("DEJA_ACTIF", "Ce compte est déjà actif.");
        }
        if (u.getRole() == Role.NOTAIRE_TITULAIRE && titulaireActifAutre(u)) {
            throw new RegleMetierException("TITULAIRE_UNIQUE", "L'office a déjà un notaire titulaire actif.");
        }
        u.setActif(true);
        u.setDesactiveLe(null);
        journal.tracer(TypeEvenementAdmin.COMPTE_REACTIVE, admin, u, null);
        return compte(id);
    }

    public CompteEtMotDePasse reinitialiser(String id, Utilisateur admin) {
        Utilisateur u = utilisateur(id);
        if (u.getId().equals(admin.getId())) {
            throw new RegleMetierException("SOI_MEME", "Changez votre propre mot de passe depuis votre profil.");
        }
        if (!u.isActif()) {
            throw new RegleMetierException("COMPTE_DESACTIVE", "Réactivez le compte avant de réinitialiser son mot de passe.");
        }
        String provisoire = politique.provisoire();
        u.setMotDePasse(encodeur.encode(provisoire));
        u.setDoitChangerMotDePasse(true);
        u.setEchecsConnexion(0);
        u.setVerrouilleJusqua(null);
        u.setVersionJeton(u.getVersionJeton() + 1);
        journal.tracer(TypeEvenementAdmin.MOT_DE_PASSE_REINITIALISE, admin, u, "Mot de passe provisoire remis · sessions fermées");
        return new CompteEtMotDePasse(compte(id), provisoire);
    }

    public CompteVue deverrouiller(String id, Utilisateur admin) {
        Utilisateur u = utilisateur(id);
        u.setVerrouilleJusqua(null);
        u.setEchecsConnexion(0);
        journal.tracer(TypeEvenementAdmin.COMPTE_DEVERROUILLE, admin, u, null);
        return compte(id);
    }

    public CompteVue revoquerSessions(String id, Utilisateur admin) {
        Utilisateur u = utilisateur(id);
        if (u.getId().equals(admin.getId())) {
            throw new RegleMetierException("SOI_MEME", "Déconnectez-vous pour fermer votre propre session.");
        }
        u.setVersionJeton(u.getVersionJeton() + 1);
        journal.tracer(TypeEvenementAdmin.SESSIONS_REVOQUEES, admin, u, "Toutes les sessions ouvertes sont fermées");
        return compte(id);
    }

    /* ------------------------------------------------------------------ *
     *  Règles
     * ------------------------------------------------------------------ */

    private List<Blocage> controlerSaisie(SaisieCompte s, Utilisateur existant) {
        List<Blocage> blocages = new ArrayList<>();
        if (s.prenom() == null || s.prenom().isBlank() || s.nom() == null || s.nom().isBlank()) {
            blocages.add(new Blocage("NOM_REQUIS", "Le prénom et le nom sont obligatoires."));
        }
        if (s.role() == null) {
            blocages.add(new Blocage("PROFIL_REQUIS", "Choisissez le profil du compte."));
        }
        String email = s.email() == null ? "" : s.email().trim();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            blocages.add(new Blocage("EMAIL_INVALIDE", "Adresse e-mail invalide."));
        } else {
            utilisateurs.findByEmailIgnoreCase(email)
                    .filter(autre -> existant == null || !autre.getId().equals(existant.getId()))
                    .ifPresent(autre -> blocages.add(new Blocage("EMAIL_UTILISE", "Cette adresse est déjà utilisée par " + autre.nomComplet() + ".")));
        }
        if (s.role() == Role.NOTAIRE_TITULAIRE && (existant == null || existant.getRole() != Role.NOTAIRE_TITULAIRE)
                && titulaireActifAutre(existant)) {
            blocages.add(new Blocage("TITULAIRE_UNIQUE", "L'office a déjà un notaire titulaire actif."));
        }
        if (s.role() == Role.NOTAIRE_SALARIE) {
            Utilisateur responsable = s.notaireResponsableId() == null || s.notaireResponsableId().isBlank() ? null
                    : utilisateurs.findById(s.notaireResponsableId()).orElse(null);
            if (responsable == null || !responsable.isActif() || !responsable.getRole().estNotaireEnTitre()) {
                blocages.add(new Blocage("RESPONSABLE_REQUIS", "Un notaire salarié exerce sous la responsabilité d'un notaire titulaire ou associé actif."));
            }
        }
        return blocages;
    }

    private void appliquerFonction(Utilisateur u, SaisieCompte s) {
        u.setReferenceNomination(s.role().estNotaire() && s.referenceNomination() != null && !s.referenceNomination().isBlank()
                ? s.referenceNomination().trim() : null);
        u.setNotaireResponsable(s.role() == Role.NOTAIRE_SALARIE ? utilisateurs.findById(s.notaireResponsableId()).orElse(null) : null);
    }

    private boolean titulaireActifAutre(Utilisateur u) {
        return utilisateurs.findByRoleAndActifTrueOrderByNom(Role.NOTAIRE_TITULAIRE).stream()
                .anyMatch(t -> u == null || !t.getId().equals(u.getId()));
    }

    private long autresSuperAdminsActifs(Utilisateur u) {
        return utilisateurs.findByRoleAndActifTrueOrderByNom(Role.SUPER_ADMIN).stream().filter(a -> !a.getId().equals(u.getId())).count();
    }

    private List<Dossier> dossiersEnCours() {
        return dossiers.findAll().stream().filter(d -> d.getStatut() != StatutDossier.CLOSED).toList();
    }

    private long dossiersDe(Utilisateur u) {
        return dossiersEnCours().stream()
                .filter(d -> (d.getClerc() != null && d.getClerc().getId().equals(u.getId())) || d.getNotaire().getId().equals(u.getId()))
                .count();
    }

    private CompteVue vue(Utilisateur u, List<Dossier> enCours) {
        long nb = enCours.stream()
                .filter(d -> (d.getClerc() != null && d.getClerc().getId().equals(u.getId())) || d.getNotaire().getId().equals(u.getId()))
                .count();
        LocalDateTime maintenant = LocalDateTime.now(clock);
        return new CompteVue(u.getId(), u.getPrenom(), u.getNom(), u.nomComplet(), u.getEmail(), u.getRole(),
                u.getRole().getLibelle(), u.getInitiales(), u.isActif(), u.estVerrouille(maintenant), u.getVerrouilleJusqua(),
                u.isDoitChangerMotDePasse(), u.getDerniereConnexion(), u.getDateEntree(), u.getDesactiveLe(),
                u.getReferenceNomination(), u.getNotaireResponsable() == null ? null : u.getNotaireResponsable().getId(),
                u.getNotaireResponsable() == null ? null : u.getNotaireResponsable().nomComplet(), nb);
    }

    private Utilisateur utilisateur(String id) {
        return utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Compte " + id + " introuvable."));
    }

    private String prochainId() {
        int n = utilisateurs.findAll().stream()
                .map(Utilisateur::getId).filter(i -> i.matches("U-\\d+"))
                .mapToInt(i -> Integer.parseInt(i.substring(2))).max().orElse(0) + 1;
        return "U-%03d".formatted(n);
    }

    static String initiales(String prenom, String nom) {
        String p = Normalizer.normalize(prenom.trim(), Normalizer.Form.NFC);
        String n = Normalizer.normalize(nom.trim(), Normalizer.Form.NFC);
        return ((p.isEmpty() ? "" : p.substring(0, 1)) + (n.isEmpty() ? "" : n.substring(0, 1))).toUpperCase(Locale.ROOT);
    }
}
