package sn.afrierp.notarius.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compte bancaire collectif de l'etude (maniement de fonds). Son solde est
 * celui du releve bancaire : c'est la source de verite du rapprochement.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class CompteCollectif {

    @Id
    private String id;

    private String libelle;
    private String banque;
    private String iban;
    private long soldeBancaire;
    private LocalDate dateDernierReleve;
}
