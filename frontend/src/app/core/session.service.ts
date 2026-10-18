import { Injectable, computed, signal } from '@angular/core';
import { Utilisateur } from './models';

const CLE = 'notarius.utilisateur';

/**
 * Utilisateur qui agit (environnement de demonstration : choisi dans l'en-tete).
 * L'API applique les controles de role cote serveur ; en production, l'identite
 * proviendra du jeton d'authentification.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly idCourant = signal<string>(lire() ?? 'U-001');
  readonly utilisateurs = signal<Utilisateur[]>([]);

  readonly id = this.idCourant.asReadonly();
  readonly utilisateur = computed(() => this.utilisateurs().find((u) => u.id === this.idCourant()));

  changer(id: string): void {
    this.idCourant.set(id);
    try {
      localStorage.setItem(CLE, id);
    } catch {
      /* stockage indisponible : la session reste en memoire */
    }
  }
}

function lire(): string | null {
  try {
    return localStorage.getItem(CLE);
  } catch {
    return null;
  }
}
