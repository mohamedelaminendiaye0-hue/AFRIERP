package sn.afrierp.notarius.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Partie a l'acte, personne physique ou morale. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Intervenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    private TypePersonne type;

    private String nom;

    @Enumerated(EnumType.STRING)
    private QualiteIntervenant qualite;

    /** NINEA pour une personne morale, CNI / passeport pour une personne physique. */
    private String identifiant;
    private String telephone;
    private String email;
    private String adresse;

    /** Fiche client rattachee : relie les interventions d'une meme personne dans tous ses dossiers. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Client client;

    /** Controle KYC / LAB-FT (lutte anti-blanchiment) realise. */
    private boolean kycValide;

    public Intervenant(TypePersonne type, String nom, QualiteIntervenant qualite, String identifiant, boolean kycValide) {
        this.type = type;
        this.nom = nom;
        this.qualite = qualite;
        this.identifiant = identifiant;
        this.kycValide = kycValide;
    }
}
