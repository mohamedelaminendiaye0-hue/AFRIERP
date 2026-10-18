package sn.afrierp.notarius.domain;

public enum NatureMouvement {
    PROVISION_CLIENT(false),
    PRIX_VENTE(false),
    DEPOT_GARANTIE(false),
    VERSEMENT_VENDEUR(true),
    PAIEMENT_TRESOR(true),
    PAIEMENT_TIERS(true),
    HONORAIRES_ETUDE(true),
    TRANSFERT_CDC(true),
    RESTITUTION_CLIENT(true),
    FRAIS_BANCAIRES(true);

    private final boolean decaissement;

    NatureMouvement(boolean decaissement) {
        this.decaissement = decaissement;
    }

    public boolean estDecaissement() {
        return decaissement;
    }
}
