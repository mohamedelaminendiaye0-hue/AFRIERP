package sn.afrierp.notarius.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Politique de sécurité de la plateforme, réglée par le super administrateur. Une seule ligne. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ParametresSecurite {

    public static final String ID = "PLATEFORME";

    @Id
    private String id = ID;

    /** Longueur minimale des mots de passe (lettres et chiffres exigés). */
    private int longueurMinimale = 10;
    /** Durée de validité d'une session, en heures. */
    private int dureeSessionHeures = 10;
    /** Échecs de connexion consécutifs avant verrouillage temporaire du compte. */
    private int tentativesAvantVerrouillage = 5;
    private int dureeVerrouillageMinutes = 15;
    /** Un compte créé ou réinitialisé reçoit un mot de passe provisoire à changer. */
    private boolean changementObligatoire = true;

    private LocalDateTime miseAJourLe;
    private String miseAJourPar;
}
