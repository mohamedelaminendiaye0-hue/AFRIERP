import { Injectable, computed, signal } from '@angular/core';
import { Role, Session, Utilisateur } from './models';

const CLE = 'notarius.session';

/**
 * Session authentifiée : jeton émis par l'API à la connexion et profil de
 * l'utilisateur. L'API contrôle chaque droit côté serveur ; le profil ne sert
 * ici qu'à adapter l'interface (menu, accueil, boutons).
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly courante = signal<Session | null>(lire());
  /** Annuaire de l'étude (listes de clercs, de notaires…), chargé après connexion. */
  readonly utilisateurs = signal<Utilisateur[]>([]);

  readonly utilisateur = computed(() => this.courante()?.utilisateur);
  readonly id = computed(() => this.courante()?.utilisateur.id ?? '');
  readonly jeton = computed(() => this.courante()?.jeton ?? null);
  readonly doitChangerMotDePasse = computed(() => !!this.courante()?.doitChangerMotDePasse);
  readonly connecte = computed(() => {
    const s = this.courante();
    return !!s && new Date(s.expiration).getTime() > Date.now();
  });

  ouvrir(session: Session): void {
    this.courante.set(session);
    ecrire(session);
  }

  fermer(): void {
    this.courante.set(null);
    this.utilisateurs.set([]);
    ecrire(null);
  }

  a(...roles: Role[]): boolean {
    const r = this.utilisateur()?.role;
    return !!r && roles.includes(r);
  }
}

function lire(): Session | null {
  try {
    const brut = localStorage.getItem(CLE);
    return brut ? (JSON.parse(brut) as Session) : null;
  } catch {
    return null;
  }
}

function ecrire(session: Session | null): void {
  try {
    if (session) localStorage.setItem(CLE, JSON.stringify(session));
    else localStorage.removeItem(CLE);
  } catch {
    /* stockage indisponible : la session reste en mémoire pour cet onglet */
  }
}
