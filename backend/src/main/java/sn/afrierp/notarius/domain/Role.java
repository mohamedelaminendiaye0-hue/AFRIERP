package sn.afrierp.notarius.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Hierarchie de l'etude notariale. Les transitions du workflow et les actions de
 * suivi sont reservees par profil.
 */
public enum Role {
    /** Officier public et ministeriel nomme par decret : monopole de l'authentification dans son ressort. */
    NOTAIRE_TITULAIRE("Notaire titulaire", "Officier public et ministeriel nomme par decret. Detient le monopole de "
            + "l'authentification des actes dans le ressort de son office et engage sa responsabilite civile, penale "
            + "et disciplinaire par sa signature et son sceau."),
    /** Exercice en groupement au sein d'une Societe Civile Professionnelle (SCP). */
    NOTAIRE_ASSOCIE("Notaire associe", "Notaire exercant en groupement au sein de la Societe Civile Professionnelle "
            + "titulaire de l'office ; authentifie les actes en son nom."),
    /** Notaire diplome lie par un contrat de travail, sous la responsabilite du titulaire. */
    NOTAIRE_SALARIE("Notaire salarie", "Notaire diplome lie a l'etude par un contrat de travail ; exerce les fonctions "
            + "notariales sous la responsabilite du notaire titulaire."),
    PRINCIPAL_CLERC("Principal clerc", "Chef d'etude, bras droit du notaire : encadre l'equipe de redaction, gere le "
            + "planning de production et supervise les dossiers complexes (ventes immobilieres, societes)."),
    CLERC("Clerc redacteur", "Recherche juridique, rassemblement des pieces administratives et redaction des projets "
            + "d'actes (compromis, statuts, actes de notoriete)."),
    COMPTABLE_TAXATEUR("Comptable-taxateur", "Calcule les droits fiscaux (enregistrement, TVA, plus-value), tient la "
            + "comptabilite des comptes sequestres et suit le paiement des frais au Tresor public."),
    FORMALISTE("Formaliste", "Depot et suivi des dossiers apres signature : mutations a la Conservation fonciere, "
            + "inscriptions au RCCM, publication d'avis legaux."),
    ARCHIVISTE("Archiviste", "Gestionnaire des minutes : conservation physique et numerique des actes originaux "
            + "pendant la duree legale minimale de 75 ans."),
    ASSISTANT("Secretariat / Accueil", "Accueil des clients, ouverture des dossiers et planification des rendez-vous.");

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

    public boolean estNotaireEnTitre() {
        return NOTAIRES_EN_TITRE.contains(this);
    }
}
