package sn.afrierp.notarius.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Hierarchie de l'etude notariale. Les transitions du workflow et les actions de
 * suivi sont reservees par profil.
 */
public enum Role {
    /** Officier public et ministeriel nomme par decret : monopole de l'authentification dans son ressort. */
    NOTAIRE_TITULAIRE("Notaire titulaire", "Officier public et ministériel nommé par décret. Détient le monopole de "
            + "l'authentification des actes dans le ressort de son office et engage sa responsabilité civile, pénale "
            + "et disciplinaire par sa signature et son sceau."),
    /** Exercice en groupement au sein d'une Societe Civile Professionnelle (SCP). */
    NOTAIRE_ASSOCIE("Notaire associé", "Notaire exerçant en groupement au sein de la Société Civile Professionnelle "
            + "titulaire de l'office ; authentifie les actes en son nom."),
    /** Notaire diplome lie par un contrat de travail, sous la responsabilite du titulaire. */
    NOTAIRE_SALARIE("Notaire salarié", "Notaire diplômé lié à l'étude par un contrat de travail ; exerce les fonctions "
            + "notariales sous la responsabilité du notaire titulaire."),
    PRINCIPAL_CLERC("Principal clerc", "Chef d'étude, bras droit du notaire : encadre l'équipe de rédaction, gère le "
            + "planning de production et supervise les dossiers complexes (ventes immobilières, sociétés)."),
    CLERC("Clerc rédacteur", "Recherche juridique, rassemblement des pièces administratives et rédaction des projets "
            + "d'actes (compromis, statuts, actes de notoriété)."),
    COMPTABLE_TAXATEUR("Comptable-taxateur", "Calcule les droits fiscaux (enregistrement, TVA, plus-value), tient la "
            + "comptabilité des comptes séquestres et suit le paiement des frais au Trésor public."),
    FORMALISTE("Formaliste", "Dépôt et suivi des dossiers après signature : mutations à la Conservation foncière, "
            + "inscriptions au RCCM, publication d'avis légaux."),
    ARCHIVISTE("Archiviste", "Gestionnaire des minutes : conservation physique et numérique des actes originaux "
            + "pendant la durée légale minimale de 75 ans."),
    ASSISTANT("Secrétariat / Accueil", "Accueil des clients, ouverture des dossiers et planification des rendez-vous."),
    /** Administrateur de la plateforme (editeur / integrateur) : hors de l'etude, sans acces aux actes. */
    SUPER_ADMIN("Super administrateur", "Administre la plateforme : identité visuelle de l'étude (marque blanche, logo, "
            + "couleurs) et paramétrage. N'intervient pas sur les dossiers.");

    /** Notaires habilites a authentifier un acte. */
    public static final Set<Role> NOTAIRES = EnumSet.of(NOTAIRE_TITULAIRE, NOTAIRE_ASSOCIE, NOTAIRE_SALARIE);
    /** Notaires « en titre » : titulaire de l'office ou associe de la SCP. Autorite sur tous les dossiers. */
    public static final Set<Role> NOTAIRES_EN_TITRE = EnumSet.of(NOTAIRE_TITULAIRE, NOTAIRE_ASSOCIE);
    /** Encadrement : voit tous les dossiers et tous les retards, reassigne les clercs. */
    public static final Set<Role> ENCADREMENT = EnumSet.of(NOTAIRE_TITULAIRE, NOTAIRE_ASSOCIE, PRINCIPAL_CLERC);

    private final String libelle;
    private final String description;

    Role(String libelle, String description) {
        this.libelle = libelle;
        this.description = description;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getDescription() {
        return description;
    }

    public boolean estNotaire() {
        return NOTAIRES.contains(this);
    }

    public boolean estMembreEtude() {
        return this != SUPER_ADMIN;
    }

    public boolean estNotaireEnTitre() {
        return NOTAIRES_EN_TITRE.contains(this);
    }
}
