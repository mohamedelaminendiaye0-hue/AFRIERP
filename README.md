# Notarius — plateforme de gestion d'étude notariale (UEMOA / OHADA, Sénégal)

Reprise du POC [`notarius`](https://github.com/mohamedelaminendiaye0-hue/notarius) en **Angular + Spring Boot**,
avec un processus métier refondu : chaque dossier suit une **machine à états stricte** de 10 statuts,
et aucune étape légale ou fiscale ne peut être sautée.

| Couche | Technologie |
|---|---|
| Frontend | Angular 18 (standalone, signals), SCSS — `frontend/` |
| API | Spring Boot 3.3, Java 21, Spring Data JPA — `backend/` |
| Base | PostgreSQL 16 (Docker) · H2 en mémoire en développement |

---

## Démarrage avec Docker (recommandé)

Seul Docker est nécessaire : Maven et Node tournent dans les images.

```bash
docker compose up --build
```

- Application : http://localhost:4200
- API : http://localhost:8080/api

Après une mise à jour du modèle, repartir d'une base vierge avec `docker compose down -v` (POC : le schéma
est généré par Hibernate ; des migrations Flyway sont prévues avant la production).

Le backend se compile avec Maven **dans** l'image (`maven:3.9.9-eclipse-temurin-21`) et lance les tests métier
pendant le build (`--build-arg SKIP_TESTS=true` pour les sauter). Le frontend est compilé dans `node:22-alpine`
puis servi par nginx, qui relaie `/api` vers le backend. Les données sont conservées dans le volume
`donnees-postgres` ; le jeu de démonstration n'est chargé que si la base est vide.

## Démarrage en développement

```bash
# API (H2 en mémoire, données de démonstration)
cd backend && ./mvnw spring-boot:run

# Frontend (proxy /api -> localhost:8080)
cd frontend && npm install && npm start      # http://localhost:4200
```

Tests métier : `cd backend && ./mvnw test`.

---

## Cycle de vie du dossier

| Statut technique | Libellé métier | Acteur responsable | Action / transition clé | Délai cible |
|---|---|---|---|---|
| `DRAFT` | Création / Brouillon | Accueil / Clerc | Saisie des informations de base ; **affectation d'un clerc** | 3 j |
| `PENDING_DOCS` | Instruction & Pièces | Clerc rédacteur | Collecte des pièces — **blocage si pièces manquantes** | 30 j |
| `DRAFTING` | Rédaction en cours | Clerc rédacteur (+ visa du principal clerc si dossier complexe) | Rédaction du projet d'acte | 15 j |
| `REVIEW` | Validation Titulaire | Notaire superviseur / notaire en titre | Relecture, correction, validation finale | 5 j |
| `TAXING` | Taxage prévisionnel | Comptable-taxateur | Calcul des frais (émoluments, timbres, enregistrement DGID, plus-value) | 3 j |
| `FUNDING` | En attente de provision | Comptable-taxateur / Client | Dépôt des fonds et de la provision sur le séquestre | 15 j |
| `READY_TO_SIGN` | Prêt pour signature | Secrétariat / Clerc | **Rendez-vous de signature** dans l'agenda | 10 j |
| `SIGNED` | Signé (minute établie) | Notaire (titulaire, associé ou salarié) | Signature et sceau ; physique ou électronique (GAINDE 2000) | 5 j |
| `POST_ACT` | Formalités en cours | Formaliste | Conservation foncière, RCCM, avis légaux — déposées puis achevées | 30 j |
| `CLOSED` | Clôturé & archivé | Archiviste (+ comptable-taxateur) | Taxage définitif, reliquat restitué, **minute archivée 75 ans** | — |

```
[Création] → [Instruction & Pièces] → [Rédaction] → [Validation notaire] → [Taxage] → [Provision]
                                                                                          │
[Archivage / Clôture] ← [Formalités post-acte] ← [Signature minute] ← [Prêt pour signature] ┘
```

Retours arrière autorisés, **avec motif obligatoire tracé** : Rédaction → Instruction, Validation → Rédaction
(renvoi pour correction), Provision → Taxage (reprise du taxage).

### Règles appliquées par le moteur (`backend/.../workflow/WorkflowService.java`)

| # | Règle |
|---|---|
| **W1** | Seules les transitions déclarées existent (`Transition.TOUTES`) : aucune étape ne peut être sautée. |
| **W2** | Chaque transition est réservée à des rôles ; validation et signature sont réservées au **notaire superviseur du dossier**. |
| **W3** | Checklist intelligente : la rédaction est bloquée tant qu'une pièce obligatoire manque ou est **périmée** (ex. état des droits réels de plus de 3 mois). Certaines pièces (mainlevée, dépôt du capital) ne sont exigées qu'à la mise à la signature. |
| **W4** | Passerelle comptable : `READY_TO_SIGN` exige une **certification comptable** — fonds encaissés sur le séquestre ≥ frais (+ prix pour une vente) — ainsi que le KYC / LAB-FT de toutes les parties et la fin de la purge. |
| **W5** | La clôture exige toutes les formalités requises **achevées**, le **taxage définitif** et un sous-compte soldé (reliquat restitué). Elle inscrit la minute au répertoire (n° `AAAA/NNNN`) avec une conservation de 75 ans. |
| **W6** | Dossier complexe (vente immobilière, société) : la soumission au notaire exige le **visa du principal clerc**. |

Le moteur rend un verdict **exhaustif** (tous les blocages, pas seulement le premier). L'interface
désactive l'action et affiche les motifs ; une tentative refusée côté API renvoie `409` avec la liste
des blocages et est tracée au journal (`TENTATIVE_BLOQUEE`).

### Acteurs de l'étude

| Profil | Rôle dans la plateforme |
|---|---|
| **Notaire titulaire** | Officier public nommé par décret. Notaire « en titre » : peut agir sur tous les dossiers, assigner clercs et notaires, signer. |
| **Notaire associé** (SCP) | Notaire en titre, mêmes prérogatives ; signe en son nom. |
| **Notaire salarié** | Valide et signe les dossiers qu'il supervise ; la signature enregistre que la **responsabilité est celle de son titulaire**. |
| **Principal clerc** | Encadre la rédaction : assigne les dossiers aux clercs (planning de production), vise les projets des dossiers complexes, voit tous les retards. |
| **Clerc rédacteur** | Instruction, pièces, KYC, rédaction, soumission au notaire. |
| **Comptable-taxateur** | Taxage prévisionnel et définitif (dont la plus-value), séquestre, certification des fonds, virements. |
| **Formaliste** | Dépôt puis achèvement des formalités (Conservation foncière, RCCM, avis légaux), suivi GAINDE. |
| **Archiviste** | Archive la minute et clôture le dossier ; tient le **minutier**. |
| **Secrétariat / accueil** | Ouverture des dossiers, prise de rendez-vous. |

L'écran **Équipe** présente cette organisation (nomination, notaire responsable d'un salarié, charge de chacun).

### Traçabilité & assignation

- Un dossier peut être ouvert **sans clerc** par l'accueil : il rejoint la file « À assigner » (filtre du portefeuille,
  indicateur du tableau de bord) et ne peut pas entrer en instruction tant qu'aucun clerc n'est affecté.
- Le **principal clerc** et les **notaires en titre** affectent le clerc rédacteur, depuis la fiche du dossier ou
  directement dans le portefeuille (avec la charge actuelle de chaque clerc). Seuls les **notaires en titre**
  changent le notaire superviseur (impossible une fois l'acte signé). Chaque assignation est tracée.
- Chaque statut a un **délai cible** (`Bareme.DELAIS_CIBLES_JOURS`) ; les dépassements génèrent des
  notifications adressées au clerc et au notaire (cloche « Retards » et tableau de bord).
- **Journal d'audit append-only** (entité `@Immutable`, aucun endpoint de modification) : qui, quoi,
  quand, et **depuis quel statut** ; détail avant / après pour les changements sensibles.

### GAINDE 2000

Pas d'intégration directe à ce stade. À la signature, le notaire choisit le mode (physique ou électronique
GAINDE 2000). Pour un dossier signé via GAINDE, un **statut de suivi manuel** est tenu : *Transmis →
Validé / Rejeté*, avec la référence de la transaction. Les formalités ne peuvent être engagées que si la
signature électronique est confirmée (statut *Validé*). Un filtre « GAINDE 2000 » liste ces dossiers.

---

## Catalogue des actes et démarches

23 types d'actes répartis en 4 domaines (écran **Catalogue des actes**, `GET /api/referentiels/types-acte`).
Chaque type porte son assiette, sa fiscalité, son émolument, son séquestre, sa checklist de pièces et ses
formalités post-acte (`Bareme.CATALOGUE`, `ModelesPieces`).

| Domaine | Actes |
|---|---|
| Immobilier, foncier & sûretés | Vente immobilière · promesse / compromis · affectation hypothécaire · mainlevée d'hypothèque · bail emphytéotique / à construction · règlement de copropriété & EDD · bail commercial |
| Sociétés & affaires (OHADA) | Constitution de société · cession de parts / actions · modification statutaire · fusion, dissolution, liquidation · cession de fonds de commerce · nantissement |
| Famille, successions & patrimoine | Succession · donation · testament · contrat de mariage · changement de régime matrimonial · partage de communauté · procuration / mandat de protection future |
| Conservation, authentification & enchères | Dépôt au rang des minutes · acte de notoriété / constat · vente aux enchères / adjudication |

Paramètres par type :
- **Assiette** : prix, crédit garanti, capital, actif net… ou **aucune** (testament, procuration, dépôt, notoriété,
  contrat de mariage) : l'acte est alors taxé au **droit fixe** avec un **émolument forfaitaire**.
- **Publicité foncière** : la taxe de conservation foncière et les débours fonciers ne s'appliquent qu'aux actes
  publiés au livre foncier.
- **Séquestre** avant signature : le **prix** (vente, cession de parts ou de fonds, adjudication) ou le **capital**
  (constitution de société), en plus des frais.
- **Formalités requises** : DGID, Conservation foncière, RCCM, NINEA, annonces légales ; le formaliste peut en
  ajouter une complémentaire (ex. **homologation judiciaire** d'un changement de régime matrimonial).

Règles propres à certaines démarches :

| Démarche | Règle appliquée par le serveur |
|---|---|
| Cession de fonds de commerce | Le prix reste séquestré : aucun versement au vendeur avant la publication de l'avis puis l'expiration du **délai d'opposition** des créanciers (30 j, paramétrable). |
| Constitution de société | Le capital déposé sur le séquestre n'est **libéré** (nature `DEBLOCAGE_CAPITAL`) qu'après l'immatriculation au RCCM. |
| Mainlevée d'hypothèque | Peut être rattachée au dossier d'**affectation hypothécaire** d'origine (contrôle du type). |
| Dossiers complexes | Visa du principal clerc avant soumission au notaire (vente, hypothèque, bail emphytéotique, copropriété, sociétés, fonds de commerce, succession, enchères). |

 (`backend/.../workflow/RendezVousService.java`)

Agenda de l'étude (vue semaine, filtres par personne et par lieu). Types : consultation, remise de pièces,
lecture de projet, **signature**, remise de fonds. Cycle : *Planifié → Confirmé → Honoré / Client absent*, ou
*Annulé* (motif obligatoire) ; un rendez-vous peut être **reporté** (nouvelle vérification complète).

| # | Règle |
|---|---|
| **R1** | Date à venir, du lundi au vendredi, entre 8 h et 18 h (`Bareme.OUVERTURE_ETUDE` / `FERMETURE_ETUDE`), 15 min à 4 h. |
| **R2** | Aucun chevauchement pour un même notaire, un même collaborateur ou une salle partagée (salle des signatures, salle de réunion). |
| **R3** | Une **signature** porte sur un dossier « Prêt pour signature », est reçue par un notaire, fixe la date de signature prévue du dossier ; une seule active par dossier. La transition « Signer la minute » exige ce rendez-vous et le marque honoré ; l'annuler retire la date prévue. |

La saisie est vérifiée en direct (`POST /api/rendez-vous/verification`) : les conflits s'affichent avant l'enregistrement.
Les rendez-vous rattachés à un dossier apparaissent dans son journal d'audit et sur sa fiche ; ceux du jour sont
sur le tableau de bord.

---

## Maniement de fonds & CDC (`backend/.../compta/ComptaService.java`)

| # | Invariant |
|---|---|
| **I1** | Aucun sous-compte auxiliaire ne peut être débiteur — le virement est refusé avant écriture. |
| **I2** | Somme des sous-comptes = solde bancaire du compte collectif. |
| **I3** | Fonds immobilisés au-delà de 90 jours → transfert CDC ou justification (pré-alerte à 75 jours). |

Les sous-comptes ne sont pas stockés : leurs soldes découlent des écritures validées.

## Paramètres réglementaires

Tous les taux, tranches, seuils et délais sont regroupés dans
[`backend/src/main/java/sn/afrierp/notarius/bareme/Bareme.java`](backend/src/main/java/sn/afrierp/notarius/bareme/Bareme.java)
(version `SN-2026.1`). **Ce sont des paramètres de démonstration** : ils doivent être validés par le conseil
juridique de l'étude avant toute exploitation réelle.

## Connexion, profils et marque blanche

L'API est fermée par défaut : chaque requête porte un **jeton JWT** (`Authorization: Bearer …`, HS256, 10 h)
obtenu par `POST /api/auth/connexion`. Seules la connexion et l'identité de l'étude (affichée sur la page de
connexion) sont publiques. Les mots de passe sont stockés en **BCrypt**. En production, fixer la clé de signature
avec la variable `JWT_SECRET` (32 caractères minimum).

Chaque profil arrive sur **son propre tableau de bord** (cartes-raccourcis vers les listes filtrées) et ne voit
que les menus et boutons de son métier ; l'API revérifie chaque droit côté serveur.

**Comptes de démonstration** — mot de passe commun : `notarius2026` (boutons « un clic » sur la page de connexion).

| E-mail | Nom | Profil |
|---|---|---|
| a.ndoye@etude-ndoye.sn | Aminata Ndoye | Notaire titulaire |
| o.sarr@etude-ndoye.sn | Ousmane Sarr | Notaire associé (SCP) |
| f.kane@etude-ndoye.sn | Fatou Kane | Notaire salarié (sous la responsabilité du titulaire) |
| m.gueye@etude-ndoye.sn | Mamadou Gueye | Principal clerc |
| a.diallo@, i.sow@, c.ndiaye@, m.faye@etude-ndoye.sn | Awa Diallo, Ibrahima Sow, Coumba Ndiaye, Moussa Faye | Clercs rédacteurs |
| c.diop@etude-ndoye.sn | Cheikh Diop | Comptable-taxateur |
| m.tall@etude-ndoye.sn | Mariama Tall | Formaliste |
| s.diouf@etude-ndoye.sn | Seynabou Diouf | Archiviste |
| n.ba@etude-ndoye.sn | Ndeye Ba | Secrétariat / accueil |
| admin@notarius.sn | Admin Plateforme | **Super administrateur** (marque blanche) |

### Espace super administrateur

Le super administrateur (`admin@notarius.sn`) administre la plateforme **sans accès aux dossiers ni aux actes**
(l'API `/api/admin/**` lui est réservée).

- **Vue d'ensemble** : comptes actifs, verrouillés, à mot de passe provisoire, connexions et échecs sur 24 h,
  comptes à surveiller, activité récente, politique en vigueur, état de la plateforme.
- **Comptes utilisateurs** : création (mot de passe provisoire affiché une seule fois), modification du profil et
  des coordonnées, réinitialisation du mot de passe, déverrouillage, fermeture des sessions, désactivation et
  réactivation, export. Les comptes ne sont jamais supprimés (traçabilité des actes). Règles de continuité :
  un seul notaire titulaire actif, au moins un super administrateur, pas de désactivation ni de changement de
  profil tant que des dossiers en cours sont confiés au compte, notaire salarié rattaché à un notaire en titre.
- **Politique de sécurité** : longueur minimale des mots de passe (lettres et chiffres exigés), durée de session,
  nombre d'échecs avant verrouillage et durée du verrouillage, mot de passe provisoire à la création.
- **Journal d'administration** (append-only) : connexions, échecs, verrouillages, actions sur les comptes,
  modifications de la politique et de l'identité, avec adresse IP ; filtres et export.
- **Sessions révocables** : chaque jeton porte la version du compte ; désactivation, réinitialisation,
  changement de profil ou fermeture des sessions invalident aussitôt les jetons ouverts.
- **Première connexion** : un compte à mot de passe provisoire n'accède qu'à l'écran « Choisissez votre mot de
  passe » ; chaque utilisateur peut ensuite changer le sien depuis le menu de son profil.

**Marque blanche** : le super administrateur (hors de l'étude, sans accès aux dossiers) règle dans
*Administration › Identité de l'étude* le nom, le monogramme ou le **logo** (PNG, JPEG ou SVG, 300 Ko),
la couleur principale, la couleur d'accent, la police et les mentions des documents. L'identité est
appliquée à toute l'interface, à la page de connexion et au titre des onglets.

## Clients, répertoire des actes et archivage

- **Fichier clients** : chaque partie à un acte est rattachée à une fiche client (retrouvée par son CNI,
  passeport ou NINEA). La fiche retrace **tous les dossiers** de la personne, quel que soit son rôle
  (vendeur ici, acquéreur ou associé ailleurs), les personnes liées et les fonds détenus.
- **Répertoire des actes** : registre légal tenu dans l'ordre chronologique. Le **numéro d'ordre est attribué à
  la signature** (`2026/0001`…) et n'est jamais réutilisé ; l'enregistrement DGID et les droits perçus y
  figurent. Récapitulatif pour le **visa trimestriel**, export Excel (CSV) et impression.
- **Minutier et archives** : les minutes signées sont conservées 75 ans. L'archivage — **unitaire ou
  groupé** — range la minute à son emplacement (liasse, coffre) et clôture le dossier ; chaque dossier non
  archivable est refusé avec son motif (formalités, taxage définitif, reliquat).
- **Actes irréversibles** (certification des fonds, signature, clôture, archivage, taxage définitif) :
  confirmation explicite avec récapitulatif ; la signature exige de ressaisir la référence du dossier.

## API (extrait)

| Méthode | Route | Rôle |
|---|---|---|
| `GET` | `/api/dossiers`, `/api/dossiers/{id}` | Portefeuille ; détail avec verdicts des transitions pour l'utilisateur |
| `POST` | `/api/dossiers` | Ouverture (statut `DRAFT`, checklist standard) |
| `POST` | `/api/dossiers/{id}/transitions` | `{ vers, motif?, modeSignature? }` |
| `PATCH` | `/api/dossiers/{id}/pieces/{pieceId}` | Statut d'une pièce |
| `POST` | `/api/dossiers/{id}/assignation` | `{ clercId?, notaireId? }` |
| `POST` | `/api/dossiers/{id}/visa-principal-clerc`, `/taxage-definitif`, `/gainde`, `/formalites`, `/formalites/{organisme}/achevement`, `/intervenants`, `/intervenants/{iid}/kyc`, `/purge/levee` | Actions de suivi |
| `GET/POST` | `/api/rendez-vous`, `/verification`, `/{id}/report`, `/{id}/statut`, `/aujourdhui`, `/referentiels` | Agenda et prise de rendez-vous |
| `POST` | `/api/auth/connexion` | `{ email, motDePasse }` → `{ jeton, expiration, utilisateur }` (public) |
| `GET/POST/PUT` | `/api/admin/tableau`, `/api/admin/comptes[/{id}[/desactivation\|reactivation\|reinitialisation\|deverrouillage\|revocation]]` | Administration des comptes (super administrateur) |
| `GET/PUT` | `/api/admin/parametres`, `GET /api/admin/journal?type=&q=` | Politique de sécurité, journal d'administration |
| `POST` | `/api/auth/mot-de-passe` | Changement de son propre mot de passe (`{ actuel, nouveau }`) |
| `GET/PUT` | `/api/identite` | Marque blanche (lecture publique, écriture super administrateur) |
| `GET/POST/PUT` | `/api/clients`, `/api/clients/{id}` | Fichier clients, fiche et historique des dossiers |
| `GET` | `/api/repertoire?annee=` | Répertoire des actes et récapitulatif du visa trimestriel |
| `GET/POST` | `/api/minutier`, `/api/minutier/archivage` | Minutier ; archivage unitaire ou groupé `{ dossierIds, emplacement }` |
| `GET` | `/api/equipe` | Organisation de l'étude |
| `GET` | `/api/dashboard`, `/api/notifications`, `/api/workflow` | Pilotage, retards, définition du workflow |
| `GET/POST` | `/api/compta/...` | Contrôle, sous-comptes, alertes CDC, relevés, virements contrôlés |
| `POST` | `/api/taxation/simulation` | Simulateur d'émoluments |

## Pistes pour la suite

- Double authentification (TOTP) pour les notaires et le super administrateur.
- Multi-études (plusieurs marques blanches sur une même instance).
- Migrations de schéma versionnées (Flyway) avant la mise en production.
- Intégration GAINDE 2000 et télé-déclaration DGID quand les API seront disponibles.
- GED réelle (dépôt des fichiers des pièces) et génération des actes depuis des modèles.
- Rapprochement bancaire automatique (import MT940 / CSV), export SYSCOHADA.
- Fiscalité exacte par acte (taux, droits fixes, abattements successoraux) à confronter au CGI avant production :
  les valeurs du catalogue sont des paramètres de démonstration.
