package sn.afrierp.notarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Identite visuelle de l'etude (marque blanche). Une seule ligne par installation :
 * nom, logo, couleurs et police s'appliquent a toute la plateforme, a la page de
 * connexion et aux documents. Modifiable uniquement par le super administrateur.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class IdentiteEtude {

    public static final String ID = "ETUDE";

    @Id
    private String id = ID;

    private String nom;
    private String sousTitre;
    /** Lettre(s) du monogramme affiche quand aucun logo n'est importe. */
    private String monogramme;
    private String couleurPrincipale;
    private String couleurAccent;
    private String police;

    /** Logo en data URL (SVG ou PNG, 300 Ko maximum). */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String logo;

    private String adresse;
    private String telephone;
    private String email;
    private String ninea;
    private String mentionPiedDePage;

    private LocalDateTime miseAJourLe;
    private String miseAJourPar;
}
