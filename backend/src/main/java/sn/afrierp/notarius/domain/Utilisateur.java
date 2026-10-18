package sn.afrierp.notarius.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Collaborateur de l'etude. Jamais supprime : on le desactive (tracabilite). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Utilisateur {

    @Id
    private String id;

    private String nom;
    private String prenom;
    @Column(unique = true)
    private String email;

    /** Empreinte BCrypt du mot de passe ; jamais exposee par l'API. */
    @JsonIgnore
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String initiales;
    private boolean actif = true;

    /* --- Sécurité du compte (administrée par le super administrateur) --- */
    /** Mot de passe provisoire : à remplacer à la prochaine connexion. */
    @Column(columnDefinition = "boolean default false")
    private boolean doitChangerMotDePasse;
    /** Incrémentée pour révoquer d'un coup toutes les sessions ouvertes du compte. */
    @Column(columnDefinition = "integer default 0")
    private int versionJeton;
    @Column(columnDefinition = "integer default 0")
    private int echecsConnexion;
    private LocalDateTime verrouilleJusqua;
    private LocalDateTime derniereConnexion;
    private LocalDate desactiveLe;
    private LocalDate dateEntree;

    /** Reference de l'acte de nomination (decret) ou de l'agrement, pour les notaires. */
    private String referenceNomination;

    /** Notaire salarie : notaire titulaire sous la responsabilite duquel il exerce. */
    @ManyToOne
    private Utilisateur notaireResponsable;

    public Utilisateur(String id, String prenom, String nom, Role role, String initiales, LocalDate dateEntree) {
        this.id = id;
        this.prenom = prenom;
        this.nom = nom;
        this.role = role;
        this.initiales = initiales;
        this.dateEntree = dateEntree;
        this.email = (prenom.charAt(0) + "." + nom).toLowerCase() + "@etude-ndoye.sn";
    }

    public boolean estVerrouille(LocalDateTime maintenant) {
        return verrouilleJusqua != null && verrouilleJusqua.isAfter(maintenant);
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }

    /**
     * Notaire qui engage sa responsabilite sur un acte authentifie par cet
     * utilisateur : lui-meme, ou son titulaire s'il est notaire salarie.
     */
    public Utilisateur notaireEngage() {
        return role == Role.NOTAIRE_SALARIE && notaireResponsable != null ? notaireResponsable : this;
    }
}
