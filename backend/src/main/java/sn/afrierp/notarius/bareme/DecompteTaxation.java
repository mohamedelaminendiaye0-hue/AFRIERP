package sn.afrierp.notarius.bareme;

import java.util.List;
import sn.afrierp.notarius.domain.TypeActe;

public record DecompteTaxation(
        TypeActe typeActe,
        long assiette,
        List<LigneDecompte> lignes,
        Totaux totaux,
        String versionBareme) {

    public enum Categorie {
        DROIT_ENREGISTREMENT, CONSERVATION_FONCIERE, EMOLUMENT_PROPORTIONNEL,
        EMOLUMENT_FORMALITES, HONORAIRE_LIBRE, DEBOURS, TVA, PLUS_VALUE
    }

    /**
     * @param acquisEtude le montant revient-il a l'etude (true) ou est-il reverse a un tiers ?
     * @param taux        taux decimal (0.05 = 5 %), null pour un forfait
     */
    public record LigneDecompte(String code, String libelle, Categorie categorie, long base, Double taux,
                                long montant, boolean acquisEtude, String reference) {
    }

    /**
     * {@code produitEtude} = emoluments + honoraires : la part revenant a l'etude.
     * {@code retenueVendeur} = taxe sur la plus-value, prelevee sur le prix revenant
     * au vendeur : elle n'entre pas dans le total a provisionner par le client.
     */
    public record Totaux(long droitsEtTaxes, long emoluments, long honoraires, long debours, long tva,
                         long totalGeneral, long produitEtude, long retenueVendeur) {
    }
}
