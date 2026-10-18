package sn.afrierp.notarius.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Client de l'etude, personne physique ou morale. Une meme personne intervient
 * dans plusieurs dossiers (vendeur ici, acquereur la, associe ailleurs) : la fiche
 * client relie toutes ces interventions et retrace son historique complet.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Client {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private TypePersonne type;

    private String nom;

    /** NINEA pour une personne morale, CNI / passeport pour une personne physique. */
    private String identifiant;
    private String telephone;
    private String email;
    private String adresse;
    /** Personne physique : regime matrimonial declare. */
    private String regimeMatrimonial;
    private String notes;

    /** Dernier controle KYC / LAB-FT valide. */
    private boolean kycValide;
    private LocalDate dateCreation;

    public Client(String id, TypePersonne type, String nom, String identifiant, LocalDate dateCreation) {
        this.id = id;
        this.type = type;
        this.nom = nom;
        this.identifiant = identifiant;
        this.dateCreation = dateCreation;
    }
}
