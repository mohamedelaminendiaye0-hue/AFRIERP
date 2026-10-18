package sn.afrierp.notarius.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ecriture du compte de maniement de fonds. Le sous-compte auxiliaire d'un
 * dossier n'est pas stocke : son solde est la somme de ses ecritures validees.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MouvementComptable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numero de piece comptable sequentiel, non reutilisable. */
    @Column(unique = true, nullable = false)
    private String numeroPiece;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Dossier dossier;

    private LocalDate dateOperation;
    private LocalDate dateValeur;

    @Enumerated(EnumType.STRING)
    private SensEcriture sens;

    /** Toujours positif ; le sens porte le signe. */
    private long montant;

    @Enumerated(EnumType.STRING)
    private NatureMouvement nature;

    private String libelle;

    @Enumerated(EnumType.STRING)
    private MoyenPaiement moyenPaiement;

    private String referenceBancaire;
    private String contrepartie;

    @Enumerated(EnumType.STRING)
    private StatutMouvement statut = StatutMouvement.VALIDE;

    private String saisiParId;
    private String valideParId;
    private LocalDateTime dateValidation;

    /** Solde du sous-compte apres passage de l'ecriture (controle d'etancheite). */
    private long soldeApres;

    private boolean rapproche;

    public long montantSigne() {
        return sens == SensEcriture.CREDIT ? montant : -montant;
    }

    public String getDossierId() {
        return dossier.getId();
    }
}
