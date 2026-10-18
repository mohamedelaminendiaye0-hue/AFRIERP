package sn.afrierp.notarius.repertoire;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.afrierp.notarius.audit.AuditService;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.common.RessourceIntrouvableException;
import sn.afrierp.notarius.domain.ContenuPieceJointe;
import sn.afrierp.notarius.domain.Dossier;
import sn.afrierp.notarius.domain.PieceJointe;
import sn.afrierp.notarius.domain.Role;
import sn.afrierp.notarius.domain.TypeEvenement;
import sn.afrierp.notarius.domain.TypePieceJointe;
import sn.afrierp.notarius.domain.Utilisateur;
import sn.afrierp.notarius.repository.ContenuPieceJointeRepository;
import sn.afrierp.notarius.repository.DossierRepository;
import sn.afrierp.notarius.repository.PieceJointeRepository;

/**
 * Preuves jointes aux actes du répertoire : PDF ou image, 15 Mo au plus.
 * Une preuve versée est définitive (ni modification ni suppression) et tracée
 * au journal du dossier avec son empreinte SHA-256.
 */
@Service
@Transactional(readOnly = true)
public class PreuveService {

    /** Profils habilités à verser une preuve. */
    static final Set<Role> DEPOSANTS = EnumSet.of(Role.NOTAIRE_TITULAIRE, Role.NOTAIRE_ASSOCIE, Role.NOTAIRE_SALARIE,
            Role.PRINCIPAL_CLERC, Role.COMPTABLE_TAXATEUR, Role.FORMALISTE, Role.ARCHIVISTE);

    private static final Map<String, String> FORMATS = Map.of(
            "application/pdf", "PDF", "image/png", "PNG", "image/jpeg", "JPEG");

    private final PieceJointeRepository pieces;
    private final ContenuPieceJointeRepository contenus;
    private final DossierRepository dossiers;
    private final AuditService audit;
    private final Clock clock;

    public PreuveService(PieceJointeRepository pieces, ContenuPieceJointeRepository contenus, DossierRepository dossiers,
                         AuditService audit, Clock clock) {
        this.pieces = pieces;
        this.contenus = contenus;
        this.dossiers = dossiers;
        this.audit = audit;
        this.clock = clock;
    }

    public List<PieceJointe> preuves(String dossierId) {
        return pieces.findByDossierIdOrderByDeposeLeDescIdDesc(dossierId);
    }

    public record Fichier(PieceJointe piece, byte[] contenu) {
    }

    public Fichier fichier(Long id) {
        PieceJointe p = pieces.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Preuve " + id + " introuvable."));
        byte[] octets = contenus.findById(id).map(ContenuPieceJointe::getContenu)
                .orElseThrow(() -> new RessourceIntrouvableException("Contenu de la preuve " + id + " introuvable."));
        return new Fichier(p, octets);
    }

    @Transactional
    public PieceJointe verser(String dossierId, TypePieceJointe type, String nomFichier, String typeContenu, byte[] octets,
                              String commentaire, Utilisateur auteur) {
        if (!DEPOSANTS.contains(auteur.getRole())) {
            throw new RegleMetierException("ROLE_NON_HABILITE", "Le versement des preuves est réservé aux notaires, au principal clerc, "
                    + "au comptable, au formaliste et à l'archiviste.");
        }
        Dossier d = dossiers.findById(dossierId).orElseThrow(() -> new RessourceIntrouvableException("Dossier " + dossierId + " introuvable."));
        if (d.getNumeroRepertoire() == null) {
            throw new RegleMetierException("ACTE_NON_INSCRIT", "Seuls les actes signés et inscrits au répertoire reçoivent des preuves.");
        }
        if (octets == null || octets.length == 0) {
            throw new RegleMetierException("FICHIER_VIDE", "Le fichier est vide.");
        }
        if (octets.length > ContenuPieceJointe.TAILLE_MAX) {
            throw new RegleMetierException("FICHIER_TROP_LOURD", "Le fichier dépasse 15 Mo.");
        }
        String format = formatReel(octets);
        if (format == null || !FORMATS.containsKey(format)) {
            throw new RegleMetierException("FORMAT_REFUSE", "Format accepté : PDF, PNG ou JPEG.");
        }
        String nom = nomFichier == null || nomFichier.isBlank() ? "preuve" : nomFichier.replaceAll("[\\\\/\\r\\n\"]", "_").trim();
        PieceJointe p = pieces.save(new PieceJointe(dossierId, type == null ? TypePieceJointe.AUTRE : type, nom, format, octets.length,
                sha256(octets), commentaire == null || commentaire.isBlank() ? null : commentaire.trim(), LocalDateTime.now(clock),
                auteur.getId(), auteur.nomComplet()));
        contenus.save(new ContenuPieceJointe(p.getId(), octets));
        audit.tracer(d, TypeEvenement.PREUVE_VERSEE, auteur, "Preuve versée au répertoire : " + p.getType().getLibelle(),
                Map.of("fichier", nom, "sha256", p.getEmpreinteSha256()));
        return p;
    }

    /** Type réel déduit des premiers octets (le type annoncé par le navigateur n'est pas fiable). */
    static String formatReel(byte[] o) {
        if (o.length >= 4 && o[0] == '%' && o[1] == 'P' && o[2] == 'D' && o[3] == 'F') {
            return "application/pdf";
        }
        if (o.length >= 8 && (o[0] & 0xFF) == 0x89 && o[1] == 'P' && o[2] == 'N' && o[3] == 'G') {
            return "image/png";
        }
        if (o.length >= 3 && (o[0] & 0xFF) == 0xFF && (o[1] & 0xFF) == 0xD8 && (o[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    public static String sha256(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
