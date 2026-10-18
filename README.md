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

## Prise de rendez-vous (`backend/.../workflow/RendezVousService.java`)

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

## Utilisateurs de démonstration

L'en-tête de l'application permet d'« agir en tant que » chacun des profils ; l'API applique les
contrôles de rôle côté serveur (en-tête `X-Utilisateur`).

| Id | Nom | Rôle |
|---|---|---|
| U-001 | Aminata Ndoye | Notaire titulaire |
| U-002 | Ousmane Sarr | Notaire associé (SCP) |
| U-010 | Fatou Kane | Notaire salarié (sous la responsabilité de U-001) |
| U-011 | Mamadou Gueye | Principal clerc |
| U-003 → U-006 | Awa Diallo, Ibrahima Sow, Coumba Ndiaye, Moussa Faye | Clercs rédacteurs |
| U-007 | Cheikh Diop | Comptable-taxateur |
| U-008 | Mariama Tall | Formaliste |
| U-012 | Seynabou Diouf | Archiviste |
| U-009 | Ndeye Ba | Secrétariat / accueil |

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
| `GET` | `/api/equipe`, `/api/minutier` | Organisation de l'étude, répertoire des minutes |
| `GET` | `/api/dashboard`, `/api/notifications`, `/api/workflow` | Pilotage, retards, définition du workflow |
| `GET/POST` | `/api/compta/...` | Contrôle, sous-comptes, alertes CDC, relevés, virements contrôlés |
| `POST` | `/api/taxation/simulation` | Simulateur d'émoluments |

## Pistes pour la suite

- Authentification (OIDC / JWT) : remplacer l'en-tête `X-Utilisateur` par l'identité du jeton.
- Migrations de schéma versionnées (Flyway) avant la mise en production.
- Intégration GAINDE 2000 et télé-déclaration DGID quand les API seront disponibles.
- GED réelle (dépôt des fichiers des pièces) et génération des actes depuis des modèles.
- Rapprochement bancaire automatique (import MT940 / CSV), export SYSCOHADA.
