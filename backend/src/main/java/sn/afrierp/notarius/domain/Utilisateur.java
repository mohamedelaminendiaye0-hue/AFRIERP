package sn.afrierp.notarius.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDate;
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
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String initiales;
    private boolean actif = true;
    private LocalDate dateEntree;

    public Utilisateur(String id, String prenom, String nom, Role role, String initiales, LocalDate dateEntree) {
        this.id = id;
        this.prenom = prenom;
        this.nom = nom;
        this.role = role;
        this.initiales = initiales;
        this.dateEntree = dateEntree;
        this.email = (prenom.charAt(0) + "." + nom).toLowerCase() + "@etude-ndoye.sn";
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }
}
