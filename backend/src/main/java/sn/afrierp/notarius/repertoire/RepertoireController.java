package sn.afrierp.notarius.repertoire;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import sn.afrierp.notarius.domain.PieceJointe;
import sn.afrierp.notarius.domain.TypePieceJointe;
import sn.afrierp.notarius.repository.PieceJointeRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.afrierp.notarius.bareme.DecompteTaxation;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.Formalite;
import sn.afrierp.notarius.domain.OrganismeFormalites;
import sn.afrierp.notarius.domain.StatutDossier;
import sn.afrierp.notarius.domain.TypeActe;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.UtilisateurRepository;
import sn.afrierp.notarius.web.UtilisateurCourant;
import sn.afrierp.notarius.web.dto.Vues.LigneMinutier;
import sn.afrierp.notarius.workflow.WorkflowService;

/**
 * Repertoire des actes (registre legal tenu dans l'ordre chronologique, numero
 * d'ordre attribue a la signature et jamais reutilise) et minutier (conservation
 * physique des minutes, archivage unitaire ou groupe).
 */
@RestController
@RequestMapping("/api")
public class RepertoireController {

    private final DossierRepository dossiers;
    private final UtilisateurRepository utilisateurs;
    private final WorkflowService workflow;
    private final PreuveService preuvesService;
    private final PieceJointeRepository pieces;

    public RepertoireController(DossierRepository dossiers, UtilisateurRepository utilisateurs, WorkflowService workflow,
                                PreuveService preuvesService, PieceJointeRepository pieces) {
        this.preuvesService = preuvesService;
        this.pieces = pieces;
        this.dossiers = dossiers;
        this.utilisateurs = utilisateurs;
        this.workflow = workflow;
    }

    public record Partie(String clientId, String nom, String qualite) {
    }

    public record Enregistrement(LocalDate dateDepot, String reference, LocalDate dateAchevement) {
    }

    /**
     * Ligne du répertoire, qui tient lieu d'archives : l'{@code etat} vaut FORMALITES
     * (minute signée, formalités en cours), A_ARCHIVER ou ARCHIVEE.
     */
    public record LigneRepertoire(String numero, LocalDate date, String dossierId, String reference, String intitule,
                                  String nature, String forme, List<Partie> parties, Enregistrement enregistrement,
                                  Long droitsEnregistrement, String notaire, String etat, List<String> blocages,
                                  String emplacement, LocalDate dateArchivage, LocalDate conservationJusquau, long nbPreuves) {
    }

    /** Recapitulatif presente au visa trimestriel du receveur de l'enregistrement. */
    public record Trimestre(int annee, int trimestre, long nbActes, String premier, String dernier, LocalDate visaAvant) {
    }

    public record Repertoire(int annee, List<Integer> annees, List<LigneRepertoire> lignes, List<Trimestre> trimestres) {
    }

    @GetMapping("/repertoire")
    @Transactional(readOnly = true)
    public Repertoire repertoire(@RequestParam(required = false) Integer annee, @UtilisateurCourant Utilisateur moi) {
        List<Dossier> inscrits = dossiers.findByNumeroRepertoireNotNullOrderByNumeroRepertoireDesc();
        List<Integer> annees = inscrits.stream().map(d -> annee(d.getNumeroRepertoire())).distinct()
                .sorted(Comparator.reverseOrder()).toList();
        int retenue = annee != null ? annee : annees.isEmpty() ? LocalDate.now().getYear() : annees.get(0);
        Function<String, String> nom = id -> id == null ? null : utilisateurs.findById(id).map(Utilisateur::nomComplet).orElse(id);
        List<Dossier> deLAnnee = inscrits.stream().filter(d -> annee(d.getNumeroRepertoire()) == retenue).toList();
        Map<String, Long> preuves = deLAnnee.isEmpty() ? Map.of() : pieces.findByDossierIdIn(deLAnnee.stream().map(Dossier::getId).toList())
                .stream().collect(Collectors.groupingBy(PieceJointe::getDossierId, Collectors.counting()));
        List<LigneRepertoire> lignes = deLAnnee.stream()
                .map(d -> new LigneRepertoire(d.getNumeroRepertoire(), d.getDateSignature(), d.getId(), d.getReference(),
                        d.getIntitule(), d.getTypeActe().getLibelle(), d.getTypeActe() == TypeActe.PROCURATION ? "Brevet" : "Minute",
                        d.getIntervenants().stream().map(i -> new Partie(i.getClient() == null ? null : i.getClient().getId(),
                                i.getNom(), i.getQualite().getLibelle())).toList(),
                        d.formalite(OrganismeFormalites.DGID)
                                .map(f -> new Enregistrement(f.getDateDepot(), f.getReference(), f.getDateAchevement())).orElse(null),
                        d.formalite(OrganismeFormalites.DGID).filter(Formalite::estAchevee)
                                .map(f -> droits(d)).orElse(null),
                        nom.apply(d.getNotaireSignataireId()), etat(d, moi), blocages(d, moi), d.getEmplacementArchive(),
                        d.getDateArchivage(), d.getConservationJusquau(), preuves.getOrDefault(d.getId(), 0L)))
                .toList();
        Map<Integer, List<LigneRepertoire>> parTrimestre = lignes.stream().filter(l -> l.date() != null)
                .collect(Collectors.groupingBy(l -> (l.date().getMonthValue() - 1) / 3 + 1, TreeMap::new, Collectors.toList()));
        List<Trimestre> trimestres = new ArrayList<>();
        parTrimestre.forEach((t, ls) -> {
            List<String> numeros = ls.stream().map(LigneRepertoire::numero).sorted().toList();
            LocalDate finTrimestre = LocalDate.of(retenue, t * 3, 1).plusMonths(1).minusDays(1);
            trimestres.add(new Trimestre(retenue, t, ls.size(), numeros.get(0), numeros.get(numeros.size() - 1),
                    finTrimestre.plusDays(10)));
        });
        return new Repertoire(retenue, annees, lignes, trimestres);
    }

    private long droits(Dossier d) {
        DecompteTaxation decompte = workflow.taxationPrevisionnelle(d);
        return decompte.lignes().stream().filter(l -> l.categorie() == DecompteTaxation.Categorie.DROIT_ENREGISTREMENT)
                .mapToLong(DecompteTaxation.LigneDecompte::montant).sum();
    }

    private static int annee(String numero) {
        return Integer.parseInt(numero.substring(0, 4));
    }

    /* ------------------------------------------------------------------ *
     *  Minutier
     * ------------------------------------------------------------------ */

    /** Archivabilité d'une minute : formalités achevées, taxage arrêté, reliquat restitué. */
    private List<String> blocages(Dossier d, Utilisateur moi) {
        if (d.getStatut() == StatutDossier.CLOSED) {
            return List.of();
        }
        return d.getStatut() == StatutDossier.POST_ACT
                ? workflow.verifier(d, StatutDossier.CLOSED, moi, null, null).blocages().stream()
                .filter(b -> !b.code().equals("ROLE_NON_HABILITE")).map(Blocage::message).toList()
                : List.of("Formalités à engager après la signature.");
    }

    private String etat(Dossier d, Utilisateur moi) {
        if (d.getStatut() == StatutDossier.CLOSED) {
            return "ARCHIVEE";
        }
        return blocages(d, moi).isEmpty() ? "A_ARCHIVER" : "FORMALITES";
    }

    /** Vue « minutier » (compatibilité) : les mêmes actes, à plat. */
    @GetMapping("/minutier")
    @Transactional(readOnly = true)
    public List<LigneMinutier> minutier(@UtilisateurCourant Utilisateur moi) {
        Function<String, String> nom = id -> id == null ? null : utilisateurs.findById(id).map(Utilisateur::nomComplet).orElse(id);
        return dossiers.findByNumeroRepertoireNotNullOrderByNumeroRepertoireDesc().stream()
                .map(d -> new LigneMinutier(d.getNumeroRepertoire(), d.getId(), d.getReference(), d.getIntitule(),
                        d.getTypeActe().getLibelle(), d.getDateSignature(), nom.apply(d.getNotaireSignataireId()),
                        nom.apply(d.getNotaireResponsableId()), d.getDateArchivage(), d.getConservationJusquau(),
                        etat(d, moi), d.getEmplacementArchive(), blocages(d, moi)))
                .toList();
    }

    /* ------------------------------------------------------------------ *
     *  Preuves jointes aux actes
     * ------------------------------------------------------------------ */

    public record PreuveVue(Long id, String dossierId, TypePieceJointe type, String typeLibelle, String nomFichier,
                            String typeContenu, long taille, String empreinteSha256, String commentaire,
                            LocalDateTime deposeLe, String deposeParNom) {

        static PreuveVue de(PieceJointe p) {
            return new PreuveVue(p.getId(), p.getDossierId(), p.getType(), p.getType().getLibelle(), p.getNomFichier(),
                    p.getTypeContenu(), p.getTaille(), p.getEmpreinteSha256(), p.getCommentaire(), p.getDeposeLe(), p.getDeposeParNom());
        }
    }

    public record TypePreuveVue(TypePieceJointe code, String libelle) {
    }

    @GetMapping("/repertoire/types-preuve")
    public List<TypePreuveVue> typesPreuve() {
        return Arrays.stream(TypePieceJointe.values()).map(t -> new TypePreuveVue(t, t.getLibelle())).toList();
    }

    @GetMapping("/repertoire/{dossierId}/preuves")
    public List<PreuveVue> preuves(@PathVariable String dossierId) {
        return preuvesService.preuves(dossierId).stream().map(PreuveVue::de).toList();
    }

    @PostMapping(value = "/repertoire/{dossierId}/preuves", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PreuveVue verser(@PathVariable String dossierId, @RequestParam("fichier") MultipartFile fichier,
                            @RequestParam(defaultValue = "AUTRE") TypePieceJointe type,
                            @RequestParam(required = false) String commentaire,
                            @UtilisateurCourant Utilisateur moi) throws IOException {
        return PreuveVue.de(preuvesService.verser(dossierId, type, fichier.getOriginalFilename(), fichier.getContentType(),
                fichier.getBytes(), commentaire, moi));
    }

    /** Contenu d'une preuve, affiché dans le navigateur (aperçu) ou téléchargé. */
    @GetMapping("/repertoire/preuves/{id}/contenu")
    public ResponseEntity<byte[]> contenu(@PathVariable Long id) {
        PreuveService.Fichier f = preuvesService.fichier(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.piece().getTypeContenu()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.piece().getNomFichier(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Empreinte-Sha256", f.piece().getEmpreinteSha256())
                .body(f.contenu());
    }

    public record DemandeArchivage(@NotEmpty List<String> dossierIds, String emplacement) {
    }

    public record ResultatArchivage(String dossierId, boolean archive, String numeroRepertoire, List<Blocage> blocages) {
    }

    /** Archivage groupe : chaque dossier est traite independamment, les refus sont detailles. */
    @PostMapping({"/repertoire/archivage", "/minutier/archivage"})
    public List<ResultatArchivage> archiver(@Valid @RequestBody DemandeArchivage demande, @UtilisateurCourant Utilisateur moi) {
        return demande.dossierIds().stream().distinct().map(id -> {
            try {
                Dossier d = workflow.archiver(id, demande.emplacement(), moi);
                return new ResultatArchivage(id, true, d.getNumeroRepertoire(), List.of());
            } catch (RegleMetierException e) {
                return new ResultatArchivage(id, false, null, e.getBlocages());
            }
        }).toList();
    }
}
