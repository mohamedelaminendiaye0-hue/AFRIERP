import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService, lireErreur } from '../../core/api.service';
import { Compte, CompteEtMotDePasse, Role, SaisieCompte } from '../../core/models';
import { SessionService } from '../../core/session.service';
import { ConfirmationComponent } from '../../shared/confirmation.component';
import { DateFrPipe } from '../../shared/format.pipes';
import { IconeComponent, NomIcone } from '../../shared/icone.component';
import { MotDePasseRemisComponent } from '../../shared/mot-de-passe-remis.component';

type Filtre = 'actifs' | 'verrouilles' | 'provisoires' | 'desactives' | 'tous';
type Action = 'desactivation' | 'reactivation' | 'deverrouillage' | 'revocation' | 'reinitialisation';

const PROFILS: { role: Role; libelle: string; aide: string }[] = [
  { role: 'NOTAIRE_TITULAIRE', libelle: 'Notaire titulaire', aide: "Un seul titulaire actif par office." },
  { role: 'NOTAIRE_ASSOCIE', libelle: 'Notaire associé', aide: 'Associé de la SCP titulaire de l’office.' },
  { role: 'NOTAIRE_SALARIE', libelle: 'Notaire salarié', aide: 'Exerce sous la responsabilité d’un notaire titulaire ou associé.' },
  { role: 'PRINCIPAL_CLERC', libelle: 'Principal clerc', aide: 'Encadre la rédaction et répartit les dossiers.' },
  { role: 'CLERC', libelle: 'Clerc rédacteur', aide: 'Instruit et rédige les dossiers qui lui sont confiés.' },
  { role: 'COMPTABLE_TAXATEUR', libelle: 'Comptable-taxateur', aide: 'Taxation, comptes clients, certification des fonds.' },
  { role: 'FORMALISTE', libelle: 'Formaliste', aide: 'Formalités après signature.' },
  { role: 'ARCHIVISTE', libelle: 'Archiviste', aide: 'Minutier et conservation des actes.' },
  { role: 'ASSISTANT', libelle: 'Secrétariat / accueil', aide: 'Accueil, rendez-vous, ouverture des dossiers.' },
  { role: 'SUPER_ADMIN', libelle: 'Super administrateur', aide: 'Administre la plateforme ; aucun accès aux dossiers.' },
];

interface Demande {
  action: Action;
  compte: Compte;
  titre: string;
  message: string;
  libelle: string;
  icone: NomIcone;
  danger: boolean;
  avertissement: string | null;
}

/** Comptes utilisateurs : création, modification, accès et cycle de vie. */
@Component({
  selector: 'app-admin-comptes',
  standalone: true,
  imports: [FormsModule, RouterLink, DateFrPipe, IconeComponent, ConfirmationComponent, MotDePasseRemisComponent],
  templateUrl: './admin-comptes.component.html',
  styleUrls: ['./admin.scss', './admin-comptes.component.scss'],
})
export class AdminComptesComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);

  readonly nouveau = input<string>();
  readonly filtre = input<string>();
  readonly compte = input<string>();

  protected readonly profils = PROFILS;
  protected readonly comptes = signal<Compte[]>([]);
  protected readonly vue = signal<Filtre>('actifs');
  protected readonly profil = signal<Role | ''>('');
  protected readonly recherche = signal('');
  /** Compte affiché dans le panneau ; « nouveau » pour la création. */
  protected readonly selection = signal<string | 'nouveau' | null>(null);
  protected readonly erreur = signal<string[] | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly demande = signal<Demande | null>(null);
  protected readonly occupe = signal(false);
  protected readonly remis = signal<{ titre: string; resultat: CompteEtMotDePasse } | null>(null);
  protected form: SaisieCompte = vide();

  protected readonly vues: { cle: Filtre; libelle: string; predicat: (c: Compte) => boolean }[] = [
    { cle: 'actifs', libelle: 'Actifs', predicat: (c) => c.actif },
    { cle: 'verrouilles', libelle: 'Verrouillés', predicat: (c) => c.actif && c.verrouille },
    { cle: 'provisoires', libelle: 'Mot de passe provisoire', predicat: (c) => c.actif && c.doitChangerMotDePasse },
    { cle: 'desactives', libelle: 'Désactivés', predicat: (c) => !c.actif },
    { cle: 'tous', libelle: 'Tous', predicat: () => true },
  ];

  protected readonly visibles = computed(() => {
    const v = this.vues.find((x) => x.cle === this.vue())!;
    const q = normaliser(this.recherche());
    return this.comptes()
      .filter(v.predicat)
      .filter((c) => !this.profil() || c.role === this.profil())
      .filter((c) => !q || normaliser(`${c.nomComplet} ${c.email} ${c.roleLibelle} ${c.id}`).includes(q));
  });
  protected readonly courant = computed(() => {
    const id = this.selection();
    return id && id !== 'nouveau' ? this.comptes().find((c) => c.id === id) ?? null : null;
  });
  protected readonly notairesEnTitre = computed(() =>
    this.comptes().filter((c) => c.actif && (c.role === 'NOTAIRE_TITULAIRE' || c.role === 'NOTAIRE_ASSOCIE')),
  );
  protected readonly aideProfil = computed(() => PROFILS.find((p) => p.role === this.roleChoisi())?.aide ?? '');
  protected readonly roleChoisi = signal<Role>('CLERC');

  ngOnInit(): void {
    const f = this.filtre();
    if (f && this.vues.some((v) => v.cle === f)) this.vue.set(f as Filtre);
    this.charger(() => {
      if (this.nouveau()) this.ouvrirCreation();
      else if (this.compte()) this.ouvrir(this.compte()!);
    });
  }

  protected compteDe(cle: Filtre): number {
    return this.comptes().filter(this.vues.find((v) => v.cle === cle)!.predicat).length;
  }

  protected ouvrir(id: string): void {
    const c = this.comptes().find((x) => x.id === id);
    if (!c) return;
    this.form = { prenom: c.prenom, nom: c.nom, email: c.email, role: c.role, dateEntree: c.dateEntree,
      referenceNomination: c.referenceNomination, notaireResponsableId: c.notaireResponsableId };
    this.roleChoisi.set(c.role);
    this.erreur.set(null);
    this.message.set(null);
    this.selection.set(id);
  }

  protected ouvrirCreation(): void {
    this.form = vide();
    this.roleChoisi.set(this.form.role);
    this.erreur.set(null);
    this.message.set(null);
    this.selection.set('nouveau');
  }

  protected fermer(): void {
    this.selection.set(null);
    this.erreur.set(null);
  }

  protected changerRole(r: Role): void {
    this.form.role = r;
    this.roleChoisi.set(r);
  }

  protected enregistrer(): void {
    this.erreur.set(null);
    const id = this.selection();
    if (id === 'nouveau') {
      this.api.creerCompte(this.form).subscribe({
        next: (r) => {
          this.charger();
          this.selection.set(r.compte.id);
          this.remis.set({ titre: `Compte créé pour ${r.compte.nomComplet}`, resultat: r });
        },
        error: (e) => this.erreur.set(motifs(e)),
      });
    } else if (id) {
      this.api.modifierCompte(id, this.form).subscribe({
        next: (c) => {
          this.message.set('Modifications enregistrées.');
          this.charger(() => this.ouvrir(c.id));
        },
        error: (e) => this.erreur.set(motifs(e)),
      });
    }
  }

  /* --- Actions sur le compte : confirmation, puis appel --- */

  protected demander(action: Action, c: Compte): void {
    const textes: Record<Action, Omit<Demande, 'action' | 'compte'>> = {
      desactivation: {
        titre: `Désactiver le compte de ${c.nomComplet} ?`, libelle: 'Désactiver le compte', icone: 'cadenas', danger: true,
        message: 'La personne ne pourra plus se connecter et ses sessions ouvertes seront fermées. Son nom reste attaché aux actes et dossiers déjà traités.',
        avertissement: null,
      },
      reactivation: {
        titre: `Réactiver le compte de ${c.nomComplet} ?`, libelle: 'Réactiver', icone: 'cle', danger: false,
        message: 'La personne pourra de nouveau se connecter avec son mot de passe.', avertissement: null,
      },
      deverrouillage: {
        titre: `Déverrouiller le compte de ${c.nomComplet} ?`, libelle: 'Déverrouiller', icone: 'cle', danger: false,
        message: 'Le compteur d’échecs est remis à zéro ; la personne peut se reconnecter immédiatement.', avertissement: null,
      },
      revocation: {
        titre: `Fermer les sessions de ${c.nomComplet} ?`, libelle: 'Fermer les sessions', icone: 'sortie', danger: false,
        message: 'Toutes les sessions ouvertes (ordinateurs, téléphones) sont fermées ; la personne devra se reconnecter.', avertissement: null,
      },
      reinitialisation: {
        titre: `Réinitialiser le mot de passe de ${c.nomComplet} ?`, libelle: 'Générer un mot de passe provisoire', icone: 'cle', danger: false,
        message: 'Un mot de passe provisoire est généré ; l’ancien cesse de fonctionner et les sessions ouvertes sont fermées.',
        avertissement: null,
      },
    };
    this.demande.set({ action, compte: c, ...textes[action] });
  }

  protected executer(): void {
    const d = this.demande();
    if (!d) return;
    this.occupe.set(true);
    this.erreur.set(null);
    const fin = () => {
      this.occupe.set(false);
      this.demande.set(null);
    };
    if (d.action === 'reinitialisation') {
      this.api.reinitialiserCompte(d.compte.id).subscribe({
        next: (r) => {
          fin();
          this.charger(() => this.ouvrir(r.compte.id));
          this.remis.set({ titre: `Nouveau mot de passe provisoire`, resultat: r });
        },
        error: (e) => { fin(); this.erreur.set(motifs(e)); },
      });
      return;
    }
    const messages: Record<Exclude<Action, 'reinitialisation'>, string> = {
      desactivation: 'Compte désactivé ; ses sessions sont fermées.',
      reactivation: 'Compte réactivé.',
      deverrouillage: 'Compte déverrouillé.',
      revocation: 'Toutes les sessions du compte sont fermées.',
    };
    const appel: Observable<Compte> = this.api.actionCompte(d.compte.id, d.action);
    appel.subscribe({
      next: (c) => {
        fin();
        this.message.set(messages[d.action as Exclude<Action, 'reinitialisation'>]);
        this.charger(() => this.ouvrir(c.id));
      },
      error: (e) => { fin(); this.erreur.set(motifs(e)); },
    });
  }

  protected exporter(): void {
    const entetes = ['Identifiant', 'Nom', 'E-mail', 'Profil', 'État', 'Dernière connexion', 'Dossiers en cours'];
    const etat = (c: Compte) => (!c.actif ? 'Désactivé' : c.verrouille ? 'Verrouillé' : c.doitChangerMotDePasse ? 'Mot de passe provisoire' : 'Actif');
    const csv = [entetes, ...this.visibles().map((c) => [c.id, c.nomComplet, c.email, c.roleLibelle, etat(c), c.derniereConnexion ?? '', String(c.dossiersActifs)])]
      .map((l) => l.map((x) => `"${x.replace(/"/g, '""')}"`).join(';')).join('\r\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    a.download = 'comptes.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }

  private charger(ensuite?: () => void): void {
    this.api.comptes().subscribe((c) => {
      this.comptes.set(c);
      ensuite?.();
    });
  }
}

function vide(): SaisieCompte {
  return { prenom: '', nom: '', email: '', role: 'CLERC', dateEntree: new Date().toISOString().slice(0, 10), referenceNomination: '', notaireResponsableId: '' };
}

function motifs(e: unknown): string[] {
  const err = lireErreur(e);
  return err.blocages.length ? err.blocages.map((b) => b.message) : [err.message];
}

function normaliser(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
