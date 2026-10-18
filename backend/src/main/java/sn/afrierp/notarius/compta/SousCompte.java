package sn.afrierp.notarius.compta;

import java.time.LocalDate;

/**
 * Sous-compte auxiliaire d'un dossier, derive de ses ecritures validees :
 * rien n'est saisi deux fois, le solde ne peut pas diverger des ecritures.
 */
public record SousCompte(
        String dossierId,
        String reference,
        String libelle,
        long solde,
        LocalDate dateOuverture,
        /* Point de depart du compteur d'immobilisation CDC. */
        LocalDate dateDernierMouvement) {
}
