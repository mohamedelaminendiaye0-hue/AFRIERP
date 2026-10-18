package sn.afrierp.notarius.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import sn.afrierp.notarius.common.Blocage;
import sn.afrierp.notarius.common.RegleMetierException;
import sn.afrierp.notarius.common.RessourceIntrouvableException;

@RestControllerAdvice
public class GestionErreurs {

    public record Erreur(String message, List<Blocage> blocages) {
    }

    @ExceptionHandler(RegleMetierException.class)
    public ResponseEntity<Erreur> regleMetier(RegleMetierException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Erreur(e.getMessage(), e.getBlocages()));
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<Erreur> introuvable(RessourceIntrouvableException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Erreur(e.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Erreur> validation(MethodArgumentNotValidException e) {
        List<Blocage> blocages = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new Blocage("CHAMP_INVALIDE", f.getField() + " : " + f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new Erreur("Saisie invalide.", blocages));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Erreur> fichierTropLourd(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new Erreur("Le fichier dépasse 15 Mo.",
                List.of(new Blocage("FICHIER_TROP_LOURD", "Le fichier dépasse 15 Mo."))));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Erreur> concurrence(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Erreur(
                "Le dossier a été modifié entre-temps par un autre utilisateur : rechargez-le.", List.of()));
    }
}
