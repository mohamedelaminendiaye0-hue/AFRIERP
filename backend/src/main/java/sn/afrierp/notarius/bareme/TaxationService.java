package sn.afrierp.notarius.bareme;

import static sn.afrierp.notarius.bareme.DecompteTaxation.Categorie.CONSERVATION_FONCIERE;
import static sn.afrierp.notarius.bareme.DecompteTaxation.Categorie.DEBOURS;
import static sn.afrierp.notarius.bareme.DecompteTaxation.Categorie.DROIT_ENREGISTREMENT;
import static sn.afrierp.notarius.bareme.DecompteTaxation.Categorie.EMOLUMENT_FORMALITES;
import static sn.afrierp.notarius.bareme.DecompteTaxation.Categorie.EMOLUMENT_PROPORTIONNEL;
import static sn.afrierp.notarius.bareme.DecompteTaxation.Categorie.HONORAIRE_LIBRE;
import static sn.afrierp.notarius.bareme.DecompteTaxation.Categorie.PLUS_VALUE;
import static sn.afrierp.notarius.bareme.DecompteTaxation.Categorie.TVA;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import org.springframework.stereotype.Service;
import sn.afrierp.notarius.bareme.DecompteTaxation.Categorie;
import sn.afrierp.notarius.bareme.DecompteTaxation.LigneDecompte;
import sn.afrierp.notarius.bareme.DecompteTaxation.Totaux;
import sn.afrierp.notarius.domain.TypeActe;

/**
 * Moteur de taxation : decomposition des frais d'acte. Sans etat : memes
 * entrees, meme decompte.
 */
@Service
public class TaxationService {

    private static final Set<Categorie> ASSIETTE_TVA = EnumSet.of(EMOLUMENT_PROPORTIONNEL, EMOLUMENT_FORMALITES, HONORAIRE_LIBRE);

    /** Emoluments proportionnels : application du bareme degressif tranche par tranche. */
    public long emolumentsProportionnels(long assiette) {
        long total = 0;
        for (Bareme.Tranche t : Bareme.TRANCHES_EMOLUMENTS) {
            if (assiette <= t.plancher()) {
                break;
            }
            long borneHaute = t.plafond() == null ? assiette : Math.min(assiette, t.plafond());
            long base = borneHaute - t.plancher();
            if (base > 0) {
                total += Math.round(base * t.taux());
            }
        }
        return Math.max(total, Bareme.EMOLUMENTS_MINIMUM);
    }

    public DecompteTaxation simuler(TypeActe typeActe, long assietteBrute, long honoraireLibreBrut) {
        return simuler(typeActe, assietteBrute, honoraireLibreBrut, null);
    }

    /**
     * @param prixAcquisition prix d'acquisition du bien par le vendeur (vente immobiliere) :
     *                        s'il est connu, la taxe sur la plus-value est calculee.
     */
    public DecompteTaxation simuler(TypeActe typeActe, long assietteBrute, long honoraireLibreBrut, Long prixAcquisition) {
        long assiette = Math.max(0, assietteBrute);
        long honoraireLibre = Math.max(0, honoraireLibreBrut);
        List<LigneDecompte> lignes = new ArrayList<>();

        double tauxEnr = Bareme.DROITS_ENREGISTREMENT.get(typeActe);
        lignes.add(new LigneDecompte("ENR", "Droits d'enregistrement de la mutation", DROIT_ENREGISTREMENT, assiette,
                tauxEnr, Math.round(assiette * tauxEnr), false,
                "Taux parametre %.2f %% — bareme %s".formatted(tauxEnr * 100, Bareme.VERSION)));

        long montantCf = Math.max(Math.round(assiette * Bareme.CONSERVATION_FONCIERE_TAUX), Bareme.CONSERVATION_FONCIERE_MINIMUM);
        lignes.add(new LigneDecompte("CF", "Conservation fonciere — inscription et publicite", CONSERVATION_FONCIERE,
                assiette, Bareme.CONSERVATION_FONCIERE_TAUX, montantCf, false,
                String.format(Locale.FRANCE, "Taux %.2f %%, minimum de perception %,d FCFA", Bareme.CONSERVATION_FONCIERE_TAUX * 100,
                        Bareme.CONSERVATION_FONCIERE_MINIMUM)));

        lignes.add(new LigneDecompte("EMO-PROP", "Emoluments proportionnels du notaire", EMOLUMENT_PROPORTIONNEL,
                assiette, null, emolumentsProportionnels(assiette), true,
                String.format(Locale.FRANCE, "Bareme degressif — minimum %,d FCFA", Bareme.EMOLUMENTS_MINIMUM)));

        lignes.add(new LigneDecompte("EMO-FORM", "Emoluments de formalites et actes annexes", EMOLUMENT_FORMALITES,
                0, null, Bareme.EMOLUMENTS_FORMALITES.get(typeActe), true,
                "Forfait par acte — bareme interne parametrable"));

        if (honoraireLibre > 0) {
            lignes.add(new LigneDecompte("HON", "Honoraires de conseil et de redaction (libres)", HONORAIRE_LIBRE,
                    0, null, honoraireLibre, true, "Convention d'honoraires signee par le client"));
        }

        for (Bareme.Debours d : Bareme.DEBOURS) {
            lignes.add(new LigneDecompte(d.code(), d.libelle(), DEBOURS, 0, null, d.montant(), false,
                    "Debours avance par l'etude, refacture a l'identique"));
        }

        long assietteTva = lignes.stream().filter(l -> ASSIETTE_TVA.contains(l.categorie())).mapToLong(LigneDecompte::montant).sum();
        long montantTva = Math.round(assietteTva * Bareme.TVA_TAUX);
        lignes.add(new LigneDecompte("TVA", "TVA sur emoluments et honoraires", TVA, assietteTva, Bareme.TVA_TAUX,
                montantTva, false, "TVA %.0f %% — assiette : emoluments + honoraires uniquement".formatted(Bareme.TVA_TAUX * 100)));

        long retenueVendeur = 0;
        if (typeActe == TypeActe.VENTE_IMMOBILIERE && prixAcquisition != null && prixAcquisition > 0) {
            long plusValue = Math.max(0, assiette - prixAcquisition);
            retenueVendeur = Math.round(plusValue * Bareme.TAUX_PLUS_VALUE);
            lignes.add(new LigneDecompte("PV", "Taxe sur la plus-value immobiliere (a la charge du vendeur)", PLUS_VALUE,
                    plusValue, Bareme.TAUX_PLUS_VALUE, retenueVendeur, false,
                    String.format(Locale.FRANCE, "Plus-value = prix %,d - acquisition %,d FCFA ; retenue sur le prix verse au vendeur",
                            assiette, prixAcquisition)));
        }

        long droitsEtTaxes = somme(lignes, DROIT_ENREGISTREMENT, CONSERVATION_FONCIERE);
        long emoluments = somme(lignes, EMOLUMENT_PROPORTIONNEL, EMOLUMENT_FORMALITES);
        long honoraires = somme(lignes, HONORAIRE_LIBRE);
        long debours = somme(lignes, DEBOURS);
        long total = droitsEtTaxes + emoluments + honoraires + debours + montantTva;

        return new DecompteTaxation(typeActe, assiette, List.copyOf(lignes),
                new Totaux(droitsEtTaxes, emoluments, honoraires, debours, montantTva, total, emoluments + honoraires,
                        retenueVendeur),
                Bareme.VERSION);
    }

    public DecompteTaxation simuler(TypeActe typeActe, long assiette) {
        return simuler(typeActe, assiette, 0);
    }

    private static long somme(List<LigneDecompte> lignes, Categorie... categories) {
        Set<Categorie> cats = EnumSet.of(categories[0], categories);
        return lignes.stream().filter(l -> cats.contains(l.categorie())).mapToLong(LigneDecompte::montant).sum();
    }
}
