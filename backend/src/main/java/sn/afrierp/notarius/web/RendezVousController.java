package sn.afrierp.notarius.web;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.afrierp.notarius.bareme.Bareme;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.common.RessourceIntrouvableException;
import sn.afrierp.notarius.domain.Salle;
import sn.afrierp.notarius.domain.TypeRendezVous;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.web.dto.Commandes;
import sn.afrierp.notarius.web.dto.Vues.RendezVousVue;
import sn.afrierp.notarius.workflow.RendezVousService;

/** Module de prise de rendez-vous : agenda, verification des creneaux, cycle du rendez-vous. */
@RestController
@RequestMapping("/api/rendez-vous")
public class RendezVousController {

    private final RendezVousService service;
    private final UtilisateurRepository utilisateurs;
    private final DossierRepository dossiers;

    public RendezVousController(RendezVousService service, UtilisateurRepository utilisateurs, DossierRepository dossiers) {
        this.service = service;
        this.utilisateurs = utilisateurs;
        this.dossiers = dossiers;
    }

    public record Referentiels(List<Map<String, Object>> types, List<Map<String, Object>> salles, String ouverture,
                               String fermeture, List<Integer> joursOuvres) {
    }

    @GetMapping("/referentiels")
    public Referentiels referentiels() {
        return new Referentiels(
                Arrays.stream(TypeRendezVous.values()).map(t -> Map.<String, Object>of("code", t.name(), "libelle", t.getLibelle(),
                        "dureeParDefaut", t.getDureeParDefautMinutes())).toList(),
                Arrays.stream(Salle.values()).map(s -> Map.<String, Object>of("code", s.name(), "libelle", s.getLibelle(),
                        "partagee", s.estPartagee())).toList(),
                Bareme.OUVERTURE_ETUDE.toString(), Bareme.FERMETURE_ETUDE.toString(),
                Bareme.JOURS_OUVRES.stream().map(j -> j.getValue()).sorted().toList());
    }

    /** Agenda d'une periode (par defaut : 7 jours a partir de {@code du}). */
    @GetMapping
    @Transactional(readOnly = true)
    public List<RendezVousVue> agenda(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        LocalDate fin = au == null ? du.plusDays(7) : au.plusDays(1);
        return service.periode(du.atStartOfDay(), fin.atStartOfDay()).stream().map(RendezVousVue::de).toList();
    }

    @GetMapping("/aujourdhui")
    @Transactional(readOnly = true)
    public List<RendezVousVue> aujourdhui(@UtilisateurCourant Utilisateur moi) {
        return service.duJour(moi).stream().map(RendezVousVue::de).toList();
    }

    /** Verification sans effet : conflits et regles, pour guider la saisie. */
    @PostMapping("/verification")
    @Transactional(readOnly = true)
    public List<Blocage> verifier(@Valid @RequestBody Commandes.NouveauRendezVous cmd) {
        return service.verifier(demande(cmd), null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RendezVousVue creer(@Valid @RequestBody Commandes.NouveauRendezVous cmd, @UtilisateurCourant Utilisateur moi) {
        return RendezVousVue.de(service.creer(demande(cmd), moi));
    }

    @PostMapping("/{id}/report")
    @Transactional
    public RendezVousVue reporter(@PathVariable Long id, @Valid @RequestBody Commandes.Report cmd, @UtilisateurCourant Utilisateur moi) {
        return RendezVousVue.de(service.reporter(id, cmd.debut(), cmd.dureeMinutes(), moi));
    }

    @PostMapping("/{id}/statut")
    @Transactional
    public RendezVousVue statut(@PathVariable Long id, @Valid @RequestBody Commandes.ChangementStatutRdv cmd,
                                @UtilisateurCourant Utilisateur moi) {
        return RendezVousVue.de(service.changerStatut(id, cmd.statut(), cmd.motif(), moi));
    }

    private RendezVousService.Demande demande(Commandes.NouveauRendezVous cmd) {
        return new RendezVousService.Demande(cmd.type(), cmd.debut(), cmd.dureeMinutes(), cmd.salle(),
                utilisateur(cmd.notaireId()), utilisateur(cmd.collaborateurId()),
                cmd.dossierId() == null || cmd.dossierId().isBlank() ? null
                        : dossiers.findById(cmd.dossierId()).orElseThrow(() -> new RessourceIntrouvableException("Dossier introuvable.")),
                cmd.clientNom(), cmd.clientTelephone(), cmd.objet());
    }

    private Utilisateur utilisateur(String id) {
        return id == null || id.isBlank() ? null
                : utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur " + id + " introuvable."));
    }
}
